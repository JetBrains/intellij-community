import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Pair;

import java.util.Map;

public class HighlightHalfRaw {
    @FXML TableView<Pair> positive;
    @FXML TableView<Pair> negative;
    public void sortPositive(SortEvent<TableView<Pair>> e) {}
    public void scrollPositive(ScrollToEvent<TableColumn<Pair, ?>> e) {}

    public void sortNegative(SortEvent<TableColumn> e) {}
    public void scrollNegative(ScrollToEvent<TableColumn<Map.Entry, ?>> e) {}
}
