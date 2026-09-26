package pkg;

import java.math.BigDecimal;

public class TestExtendingSubclass {

  class Subclass1 {
    Subclass1(String name) {
    }
  }

  class Subclass2 extends Subclass1 {
    Subclass2(String name) {
      super(name);
    }
  }

}
