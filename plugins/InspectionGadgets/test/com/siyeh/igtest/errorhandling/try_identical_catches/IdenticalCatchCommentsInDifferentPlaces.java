class C {
  void foo() {
    try {
      bar();
    }
    catch (NullPointerException e) {
      //skip
    }
    <warning descr="'catch' branch identical to 'NullPointerException' branch">catch /*skip*/ (IllegalStateException e)</warning> {
      //
    }
    <warning descr="'catch' branch identical to 'NullPointerException' branch">catch (/*skip*/ IllegalArgumentException e)</warning> {
      //
    }
    <warning descr="'catch' branch identical to 'NullPointerException' branch">catch (IndexOutOfBoundsException /*skip*/ e)</warning> {
      //
    }
    <warning descr="'catch' branch identical to 'NullPointerException' branch">catch (<error descr="Cannot resolve symbol 'IllegalMonitorStateException'">IllegalMonitorStateException</error> e)</warning> /*skip*/ {
      //
    }
    <warning descr="'catch' branch identical to 'NullPointerException' branch">catch (ArithmeticException <caret>e)</warning> //skip
    {
      //
    }
    catch (ClassCastException e) { }
    catch // other
    (RuntimeException e) {
      //skip
    }
  }

  void bar(){}
}