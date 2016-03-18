import javafx.fxml.FXML;
import javafx.scene.control.*;

public class HighlightSimpleGenericController {
    @FXML ListView<String> list;

    public void onEditStart(ListView.EditEvent<?> e) {}
    public void onEditCancel(ListView.EditEvent<String> e) {}
    public void onEditCommit(ListView.EditEvent e) {}
}
