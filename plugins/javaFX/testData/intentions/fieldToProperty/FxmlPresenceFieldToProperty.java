import java.math.BigDecimal;

class BigDecimalDemo {
  private BigDecimal nu<caret>mber = new BigDecimal(42);

  BigDecimalDemo(BigDecimal number) {
    this.number = number;
  }

  public BigDecimal get() {
    return number;
  }

  public BigDecimal get(int i) {
    return number;
  }
}