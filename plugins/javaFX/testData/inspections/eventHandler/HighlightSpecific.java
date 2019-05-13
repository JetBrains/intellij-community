import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Pair;

import java.util.Map;

public class HighlightSpecific {
    @FXML TableView<Pair<Integer, String>> positive;
    @FXML TableView<Pair<Integer, String>> negative;
    public void sortPositive(SortEvent<TableView<Pair<Integer, String>>> e) {}
    public void scrollPositive(ScrollToEvent<TableColumn<Pair<Integer, String>, ?>> e) {}

    public void sortNegative(SortEvent<TableColumn> e) {}
    public void scrollNegative(ScrollToEvent<TableColumn<Pair<Double, String>, ?>> e) {}
}
