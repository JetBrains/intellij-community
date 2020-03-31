class Test {
  Test(java.util.List<java.lang.String> l) {
    new A().te<caret>st(1, new java.lang.Thread(), new java.lang.Runnable(){
      @Override
      void run() {

      }
    }, l)
  }
}