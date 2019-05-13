package model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class Data {
  private StringProperty fooProp = new SimpleStringProperty("");

  public StringProperty fooPropProperty() {
    return fooProp;
  }

  public String getFooProp() {
    return fooProp.getValue();
  }

  public void setFooProp(String value) {
    fooProp.setValue(value);
  }
}