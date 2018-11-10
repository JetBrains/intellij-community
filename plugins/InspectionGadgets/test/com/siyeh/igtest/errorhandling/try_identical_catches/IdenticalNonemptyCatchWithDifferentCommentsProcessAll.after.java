class C {
  void foo() {
    try {
      bar();
    } catch (NullPointerException | IllegalStateException | ClassCastException e) {
      /*same comment*/
      // comment 1
      e.printStackTrace();
    } // comment 2
    // comment 3

  }

  void bar(){}
}