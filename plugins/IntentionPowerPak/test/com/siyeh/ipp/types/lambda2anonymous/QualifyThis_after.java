class Test {
  public void m() {
    Runnable r = new Runnable() {
        @Override
        public void run() {
            <selection>System.out.println(Test.this);</selection>
        }
    };
    r.run();
  }
}