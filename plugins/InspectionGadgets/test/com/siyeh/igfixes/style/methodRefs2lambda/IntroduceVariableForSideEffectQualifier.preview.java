class Test {
  {
    Runnable runnable = () -> new Runnable() {
        {
        }

        public void run() {
            System.out.println(this);
        }
    }.run();
    runnable.run();
    runnable.run();
  }
}