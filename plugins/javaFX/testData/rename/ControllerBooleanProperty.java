import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

public class ControllerBooleanProperty {
  private BooleanProperty flag = new SimpleBooleanProperty(false);

  public BooleanProperty flagProperty() {
    return flag;
  }

  public boolean getFlag() {
    return flag.get();
  }

  public void setFlag(boolean flag) {
    this.flag.set(flag);
  }
}