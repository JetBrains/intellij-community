abstract class A {
  public A() {
    Runnable r = () -> foo();
    Runnable r1 = new Runnable() {
      public void run() {
        foo();
      }
    };

  }

  abstract void foo();
}