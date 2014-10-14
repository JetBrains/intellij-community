/*
 * Weird comment here.
 */
package pkg;

class TestLineNumbersMatch {
  void m1(boolean b) {
    if (b)
      System.out.println("a");
    else
      System.out.println("b");
  }

  void m2() {
    new Runnable() {
      @Override
      public void run() {
        System.out.println("run with me");
      }
    }.run();
  }
}