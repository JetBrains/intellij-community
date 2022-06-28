class C {
  void foo() {
    try {
      bar();
    }
    catch (NumberFormatException e) {
      e = null;
      //non final
    }
    catch (NullPointerException | IllegalStateException | ClassCastException e) {
      /*same comment*/
      // comment 1
      e.printStackTrace();
    } //line comment
    // comment 2
    // comment 3

  }

  void bar(){}
}