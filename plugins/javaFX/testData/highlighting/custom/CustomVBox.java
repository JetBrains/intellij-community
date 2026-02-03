import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;


public class CustomVBox extends VBox {
    @FXML
    private TextField tf;
    @FXML
    private Label lab1;
    @FXML
    void myMethod(){
        lab1.setText(tf.getText());
    }
}