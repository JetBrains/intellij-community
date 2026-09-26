class Test {
  {
    new Runnable() {
      void run() {
        A.<caret>f(this)
      }
    }
  }
}
