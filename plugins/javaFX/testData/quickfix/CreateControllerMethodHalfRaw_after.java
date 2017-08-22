import javafx.fxml.FXML;
import javafx.scene.control.SortEvent;
import javafx.scene.control.TableView;

import java.util.Map;

public class CreateControllerMethodHalfRaw {
    @FXML
    TableView<Map> table;

    public void onSort(SortEvent<TableView<Map>> tableViewSortEvent) {
    }
}
