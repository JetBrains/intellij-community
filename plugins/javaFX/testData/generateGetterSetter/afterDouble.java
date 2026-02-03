import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
public class Test {
    public double getField() {
        return field.get();
    }

    public DoubleProperty fieldProperty() {
        return field;
    }

    public void setField(double field) {
        this.field.set(field);
    }

    private DoubleProperty field = new SimpleDoubleProperty();
}