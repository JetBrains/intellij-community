import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Label;

class FxIdExactOptionsGrandBase {
    @FXML
    private Label grandLabel;
}

class FxIdExactOptionsBase extends FxIdExactOptionsGrandBase {
    @FXML
    private Label parentPrivateLabel;
    public Label parentPublicLabel;

    public Button parentButton;
    public Control parentControl;
    public FxIdExactOptionsModel parentModel;

    @FXML
    private String parentPrivateString;
    public String parentPublicString;
}

public class FxIdExactOptionsController extends FxIdExactOptionsBase {
    @FXML
    private Label privateLabel;
    public Label publicLabel;

    public Button button;
    public Control control;
    public FxIdExactOptionsModel model;

    @FXML
    private String privateString;
    public String publicString;
}
