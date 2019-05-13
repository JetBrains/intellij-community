import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

public class ControllerInExpressionWrapper {
    private ObjectProperty<ControllerInExpression> controller = new SimpleObjectProperty<>(this, "controller");

    public ObjectProperty<ControllerInExpression> controllerProperty() {
        return controller;
    }

    public ControllerInExpression getController() {
        return controller.get();
    }

    public void setController(ControllerInExpression controller) {
        this.controller.set(controller);
    }
}
