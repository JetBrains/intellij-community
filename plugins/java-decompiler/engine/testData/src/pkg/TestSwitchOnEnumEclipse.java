package pkg;

import java.util.concurrent.TimeUnit;

public class TestSwitchOnEnumEclipse {

  int myInt;

  public int testSOE(TimeUnit t) {
    switch (t) {
      case MICROSECONDS:
        return 2;
      case SECONDS:
        return 1;
    }
    return 0;
  }

  static class Example {

    enum A { A1, A2 }

    enum B { B1, B2 }

    void test(A a, B b) {
      switch (a) {
        case A1:
          System.out.println("A1");
          break;
        case A2:
          System.out.println("A2");
          break;
      }
      switch (b) {
        case B1:
          System.out.println("B1");
          break;
        case B2:
          System.out.println("B2");
          break;
      }
    }
  }
}