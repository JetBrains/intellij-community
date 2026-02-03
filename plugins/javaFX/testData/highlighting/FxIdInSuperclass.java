import javafx.fxml.FXML;
import javafx.scene.control.Button;

public class FxIdInSuperclass {
    @FXML
    private Button <warning descr="Private field 'inheritedButton' is assigned but never accessed">inheritedButton</warning>;
    @FXML
    private Button <warning descr="Private field 'unusedButton' is never used">unusedButton</warning>;
}