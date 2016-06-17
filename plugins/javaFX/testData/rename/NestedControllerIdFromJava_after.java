import javafx.fxml.FXML;
import javafx.scene.layout.VBox;

class NestedControllerIdFromJava {
  @FXML
  private VBox newName;
  @FXML NestedControllerIdFromJavaInternal newNameController;
}