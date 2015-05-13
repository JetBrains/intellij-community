class Test {
  {
      Runnable runnable1 = new Runnable() {
          public void run() {
              System.out.println(this);
          }
      };
      Runnable runnable = () -> runnable1.run();
    runnable.run();
    runnable.run();
  }
}