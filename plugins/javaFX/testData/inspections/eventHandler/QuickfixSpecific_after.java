import javafx.fxml.FXML;
import javafx.scene.control.SortEvent;
import javafx.scene.control.TableView;
import javafx.util.Pair;

public class QuickfixSpecific {
    @FXML
    TableView<Pair<Integer, String>> table;

    public void onSort(SortEvent<TableView<Pair<Integer, String>>> tableViewSortEvent) {
    }
}