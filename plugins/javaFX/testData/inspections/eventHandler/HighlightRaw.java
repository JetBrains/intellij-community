import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.*;

public class HighlightRaw {
    @FXML TableView positive;
    @FXML TableView negative;

    public void sortPositive(SortEvent e) {}
    public void scrollPositive(ScrollToEvent<TableColumn> e) {}

    public void sortNegative(ScrollToEvent e) {}
    public void scrollNegative(ScrollToEvent<TableView> e) {}
}
