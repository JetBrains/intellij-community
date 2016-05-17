import javafx.event.ActionEvent;
import javafx.fxml.FXML;

public class EventHandlerInSuperclass {
    @FXML
    private void onAction(ActionEvent e) { System.out.println(e); }
    @FXML
    private void <warning descr="Private method 'onUnused(javafx.event.ActionEvent)' is never used">onUnused</warning>(ActionEvent e) { System.out.println(e); }
}