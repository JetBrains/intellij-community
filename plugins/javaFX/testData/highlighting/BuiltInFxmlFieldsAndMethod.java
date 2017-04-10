import javafx.fxml.FXML;
import java.net.URL;
import java.util.ResourceBundle;

class BuiltInFxmlFieldsAndMethod {
  @FXML
  private ResourceBundle resources;
  @FXML
  private URL location;

  @FXML
  private ResourceBundle <warning descr="Private field 'resources1' is never assigned">resources1</warning>;
  @FXML
  private URL <warning descr="Private field 'location1' is never assigned">location1</warning>;

  @FXML
  private void initialize() {
    System.out.println(resources);
    System.out.println(location);
  }

  @FXML
  private void <warning descr="Private method 'initialize1()' is never used">initialize1</warning>() {
    System.out.println(resources1);
    System.out.println(location1);
  }
}