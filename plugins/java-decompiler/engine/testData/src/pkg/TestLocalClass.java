package pkg;

import java.lang.Override;
import java.lang.Runnable;

public abstract class TestLocalClass {
  void foo() {
    int a =5;
    class Local{
      void foo() {
        int b = 5;
        int v = 5;
      }
    };
    Local l = new Local();
    l.foo();
  }

  void boo() {
    int a =5;
  }

  void zoo() {
    int a =5;
  }
}
