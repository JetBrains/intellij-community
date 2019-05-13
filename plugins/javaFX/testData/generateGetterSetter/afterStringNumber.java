import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

public class BugTest {

    private ObjectProperty<String> name = new SimpleObjectProperty<>();
    private ObjectProperty<Number> value = new SimpleObjectProperty<>();

    public String getName() {
        return name.get();
    }

    public ObjectProperty<String> nameProperty() {
        return name;
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public Number getValue() {
        return value.get();
    }

    public ObjectProperty<Number> valueProperty() {
        return value;
    }

    public void setValue(Number value) {
        this.value.set(value);
    }
}