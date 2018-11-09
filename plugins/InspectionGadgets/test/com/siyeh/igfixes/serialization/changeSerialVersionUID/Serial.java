import java.io.Serializable;

public class Serial implements Serializable {
  private static final long serialVersionUID = <caret>123L;

  void foo() {}

  int m = 2;
}