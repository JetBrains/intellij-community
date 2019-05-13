import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class ControllerStringProperty {
  private StringProperty text = new SimpleStringProperty("");

  public StringProperty textProperty() {
    return text;
  }

  public String getText() {
    return text.getValue();
  }

  public void setText(String text) {
    this.text.setValue(text);
  }
}