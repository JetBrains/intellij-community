import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.*;

public class HighlightSuper {
    @FXML private void onSameArg(MouseEvent e) {}
    @FXML private void onSuperArg(Event e) {}
    @FXML private void onNoArg() {}
    @FXML private void onNotSuper(MouseDragEvent e) {}
    @FXML private void onNotRelated(SortEvent e) {}
}
