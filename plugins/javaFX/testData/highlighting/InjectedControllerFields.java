import javafx.fxml.FXML;
import javafx.scene.control.Button;

public class InjectedControllerFields {
  @FXML
  private Button id1;

  @FXML
  private Button <warning descr="Private field 'idUnmapped' is never assigned">idUnmapped</warning>;
  
  {
    System.out.println(id1);
    System.out.println(idUnmapped);
  }
  
}