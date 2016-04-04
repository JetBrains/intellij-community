import javafx.fxml.FXML;
import javafx.scene.control.SortEvent;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.Map;

public class QuickfixFieldType {
    @FXML
    TableView<Map<String, Double>> table;

    public void onSort(SortEvent<TableView<Map<String, Double>>> tableViewSortEvent) {
    }
}