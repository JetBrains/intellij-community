package injected;

import javafx.fxml.FXMLLoader;
import javafx.scene.layout.VBox;

import java.io.IOException;


public class FooVBox extends VBox{
    private void loaderCreation() {
        MyController controller =  new MyController();
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("injectedController.fxml"));
        fxmlLoader.setRoot(this);
        fxmlLoader.setController(controller);

        try {
            fxmlLoader.load();
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }
    public FooVBox() {
        loaderCreation();
    }
}
