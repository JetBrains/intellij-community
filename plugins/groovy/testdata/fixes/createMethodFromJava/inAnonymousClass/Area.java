class Test {
  {
    new Runnable() {
      public void run() {
        A.<caret>f(this);
      }
    };
  }
}