import javafx.fxml.FXML;
import javafx.scene.control.SortEvent;
import javafx.scene.control.TableView;

import java.util.Map;

public class CreateControllerMethodGeneric {
    @FXML
    TableView<Map<Integer, String>> table;

    public void onSort(SortEvent<TableView<Map<Integer, String>>> tableViewSortEvent) {
    }
}
