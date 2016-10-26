import javafx.beans.property.SimpleObjectProperty;

import java.math.BigDecimal;

class BigDecimalDemo {
    private SimpleObjectProperty<BigDecimal> number = new SimpleObjectProperty<>(this, "number", new BigDecimal(42));

  BigDecimalDemo(BigDecimal number) {
    this.number.set(number);
  }

  public BigDecimal get() {
    return number.get();
  }

  public BigDecimal get(int i) {
    return number.get();
  }
}