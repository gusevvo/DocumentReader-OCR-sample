package com.regula.documentreader;

import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import com.regula.documentreader.api.DocumentReader;
import com.regula.documentreader.api.enums.DocReaderAction;
import com.regula.documentreader.api.enums.DocReaderFrame;
import com.regula.documentreader.api.results.DocumentReaderResults;
import com.regula.documentreader.api.results.DocumentReaderScenario;
import com.regula.documentreader.api.results.DocumentReaderTextField;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {


    private Button scanButton;
    private List<Map<String, String>> resultData = new ArrayList<>();
    private SimpleAdapter resultAdapter;
    private AlertDialog loadingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        scanButton = findViewById(R.id.button_scan);

        ListView resultList = findViewById(R.id.list_result);
        resultAdapter = new SimpleAdapter(this, resultData,
                android.R.layout.simple_list_item_2,
                new String[]{"name", "value"},
                new int[]{android.R.id.text1, android.R.id.text2});
        resultList.setAdapter(resultAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!DocumentReader.Instance().getDocumentReaderIsReady()) {
            final AlertDialog initDialog = showDialog("Initializing");

            //Reading the license from raw resource file
            try {
                InputStream licInput = getResources().openRawResource(R.raw.regula);
                int available = licInput.available();
                final byte[] license = new byte[available];
                //noinspection ResultOfMethodCallIgnored
                licInput.read(license);

                //preparing database files, it will be downloaded from network only one time and stored on user device
                DocumentReader.Instance().prepareDatabase(MainActivity.this, "Full", new
                        DocumentReader.DocumentReaderPrepareCompletion() {
                            @Override
                            public void onPrepareProgressChanged(int progress) {
                                initDialog.setTitle("Downloading database: " + progress + "%");
                            }

                            @Override
                            public void onPrepareCompleted(boolean status, String error) {

                                //Initializing the reader
                                DocumentReader.Instance().initializeReader(MainActivity.this, license, new DocumentReader.DocumentReaderInitCompletion() {
                                    @Override
                                    public void onInitCompleted(boolean success, String error) {
                                        if (initDialog.isShowing()) {
                                            initDialog.dismiss();
                                        }

                                        DocumentReader.Instance().customization().setShowHelpAnimation(false);

                                        //initialization successful
                                        if (success) {
                                            scanButton.setOnClickListener(new View.OnClickListener() {
                                                @Override
                                                public void onClick(View view) {
                                                    clearResults();

                                                    //starting video processing
                                                    DocumentReader.Instance().showScanner(completion);
                                                }
                                            });

                                            //getting current processing scenario and loading available scenarios to ListView
                                            ArrayList<String> scenarios = new ArrayList<>();
                                            for (DocumentReaderScenario scenario : DocumentReader.Instance().availableScenarios) {
                                                scenarios.add(scenario.name);
                                            }

                                            DocumentReader.Instance().processParams().scenario = "Ocr";
                                            DocumentReader.Instance().functionality().setCameraFrame(DocReaderFrame.MAX);

                                        }
                                        //Initialization was not successful
                                        else {
                                            Toast.makeText(MainActivity.this, "Init failed:" + error, Toast.LENGTH_LONG).show();
                                        }
                                    }
                                });
                            }
                        });

                licInput.close();

            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }


    @Override
    protected void onPause() {
        super.onPause();

        if (loadingDialog != null) {
            loadingDialog.dismiss();
            loadingDialog = null;
        }
    }

    //DocumentReader processing callback
    private DocumentReader.DocumentReaderCompletion completion = new DocumentReader.DocumentReaderCompletion() {
        @Override
        public void onCompleted(int action, DocumentReaderResults results, String error) {
            //processing is finished, all results are ready
            if (action == DocReaderAction.COMPLETE) {
                if (loadingDialog != null && loadingDialog.isShowing()) {
                    loadingDialog.dismiss();
                }
                displayResults(results);
            } else {
                //something happened before all results were ready
                if (action == DocReaderAction.CANCEL) {
                    Toast.makeText(MainActivity.this, "Scanning was cancelled", Toast.LENGTH_LONG).show();
                } else if (action == DocReaderAction.ERROR) {
                    Toast.makeText(MainActivity.this, "Error:" + error, Toast.LENGTH_LONG).show();
                }
            }
        }
    };

    private AlertDialog showDialog(String msg) {
        AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this);
        View dialogView = getLayoutInflater().inflate(R.layout.simple_dialog, null);
        dialog.setTitle(msg);
        dialog.setView(dialogView);
        dialog.setCancelable(false);
        return dialog.show();
    }

    //show received results on the UI
    private void displayResults(DocumentReaderResults results) {
        if (results != null) {
            // through all text fields
            if (results.textResult != null && results.textResult.fields != null) {
                for (DocumentReaderTextField textField : results.textResult.fields) {
                    Map<String, String> map = new HashMap<>(2);
                    map.put("name", textField.fieldName + "(" + textField.fieldType + ")");
                    map.put("value", results.getTextFieldValueByType(textField.fieldType, textField.lcid));
                    resultData.add(map);
                }
                resultAdapter.notifyDataSetChanged();
            }
        }
    }

    private void clearResults() {
        resultData.clear();
    }

}
