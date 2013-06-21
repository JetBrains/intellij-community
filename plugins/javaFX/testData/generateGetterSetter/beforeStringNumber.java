import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

public class BugTest {

    private ObjectProperty<String> name = new SimpleObjectProperty<>();
    private ObjectProperty<Number> value = new SimpleObjectProperty<>();

    <caret>
}