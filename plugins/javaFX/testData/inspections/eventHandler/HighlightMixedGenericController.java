import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Pair;
import java.util.Map;

public class HighlightMixedGenericController {
    @FXML TableView<Pair<Integer, String>> specificTable;
    @FXML TableView<Pair<Integer, String>> specificTableHalfRawArg;
    @FXML TableView<Pair<Integer, String>> specificTableIncompatibleArg;
    @FXML TableView<Pair<Integer, String>> specificTableIncompatibleHalfRawArg;

    @FXML TableView<Pair> halfRawTable;
    @FXML TableView<Pair> halfRawTableHalfRawArg;
    @FXML TableView<Pair> halfRawTableIncompatibleArg;
    @FXML TableView<Pair> halfRawTableIncompatibleHalfRawArg;

    @FXML TableView rawTable;
    @FXML TableView rawTableHalfRawArg;
    @FXML TableView rawTableIncompatibleArg;
    @FXML TableView rawTableIncompatibleHalfRawArg;

    public void onSort(SortEvent<TableView<Pair<Integer, String>>> e) {}
    public void onScrollTo(ScrollToEvent<Integer> e) {}
    public void onScrollToColumn(ScrollToEvent<TableColumn<Pair<Integer, String>, ?>> e) {}

    public void onSortHalfRawArg(SortEvent<TableView> e) {}
    public void onScrollToHalfRawArg(ScrollToEvent<? extends Number> e) {}
    public void onScrollToColumnHalfRawArg(ScrollToEvent<TableColumn> e) {}

    public void onSortIncompatibleArg(SortEvent<TableView<Pair<Double, String>>> e) {}
    public void onScrollToIncompatibleArg(ScrollToEvent<Double> e) {}
    public void onScrollToColumnIncompatibleArg(ScrollToEvent<TableColumn<Map.Entry<Integer, String>, ?>> e) {}

    public void onSortIncompatibleHalfRawArg(SortEvent<Pair<?, String>> e) {}
    public void onScrollToIncompatibleHalfRawArg(ScrollToEvent<? super Number> e) {}
    public void onScrollToColumnIncompatibleHalfRawArg(ScrollToEvent<TableCell<Pair, ?>> e) {}
}
