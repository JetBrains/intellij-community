import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;

public class ControllerInExpression {
    @FXML
    private ControllerInExpressionWrapper wrapper;
    @FXML
    private ControllerInExpressionWrapper wrapper2;

    private StringProperty strProp = new SimpleStringProperty("Text");
    public StringProperty strPropProperty() {
    return strProp;
  }
    public String getStrProp() {
        return strProp.get();
    }
    public void setStrProp(StringProperty strProp) {
        this.strProp = strProp;
    }

    private IntegerProperty intProp = new SimpleIntegerProperty(123);
    public IntegerProperty intPropProperty() {
        return intProp;
    }
    public int getIntProp() {
        return intProp.get();
    }
    public void setIntProp(int intProp) {
        this.intProp.set(intProp);
    }

    private DoubleProperty doubleProp = new SimpleDoubleProperty(123.45);
    public DoubleProperty doublePropProperty() {
        return doubleProp;
    }
    public double getDoubleProp() {
        return doubleProp.get();
    }
    public void setDoubleProp(double doubleProp) {
        this.doubleProp.set(doubleProp);
    }

    @Override
    public String toString() {
      return "222.2";
    }
}
