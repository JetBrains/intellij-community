class C {
  void foo() {
    try {
      bar();
    } catch (NullPointerException e) {
      /*same comment*/
      // comment 1
      e.printStackTrace();
    } <warning descr="'catch' branch identical to 'NullPointerException' branch">catch (IllegalStateException e)</warning> {
      /*same comment*/
      e.printStackTrace();
      // comment 2
    } <warning descr="'catch' branch identical to 'NullPointerException' branch">catch (ClassCastException <caret>e)</warning> {
      /* same comment */
      // comment 3
      e.printStackTrace();
    }
  }

  void bar(){}
}