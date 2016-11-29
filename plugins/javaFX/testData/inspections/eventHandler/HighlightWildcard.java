import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class HighlightWildcard {
    @FXML private void scrollPositive(ScrollToEvent<? extends Number> e) {}
    @FXML private void scrollNegative(ScrollToEvent<? super Number> e) {}
}
