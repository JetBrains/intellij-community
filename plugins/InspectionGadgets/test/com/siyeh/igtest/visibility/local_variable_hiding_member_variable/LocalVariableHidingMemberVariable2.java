class Outer {
  String field;

  void localHidesField() {
    // "Local variable hides field" was designed to detect this
    String <warning descr="Local variable 'field' hides field in class 'Outer'">field</warning> = "hello";
    System.out.println(field);
  }
  class Inner {
    void innerLocalHidesOuterField() {
      // "Local variable hides field" was designed to detect this
      String <warning descr="Local variable 'field' hides field in class 'Outer'">field</warning> = "hello";
      System.out.println(field);
    }
  }

  static void localHidesOuterField() {
    // "Ignore local variables in static methods" option toggles this warning
    String <warning descr="Local variable 'field' hides field in class 'Outer'">field</warning> = "hello";
    System.out.println(field);
  }

  static class InnerStatic {
    void staticInnerLocalHidesOuterField() {
      // Invalid warning, because inner class can't access instance method, even if it wanted to.
      String <warning descr="Local variable 'field' hides field in class 'Outer'">field</warning> = "hello";
      System.out.println(field);
    }
  }
  static void staticInnerLocalHidesOuterField() {
    new Runnable() {
      @Override public void run() {
        // Invalid warning, because inner class can't access instance method, even if it wanted to.
        String <warning descr="Local variable 'field' hides field in class 'Outer'">field</warning> = "hello";
        System.out.println(field);
      }
    }.run();
  }

  static void exceptionToTheRule(final Outer outer) {
    // Of course there's an exception to "can't access instance method, even if it wanted to."
    new Runnable() {
      @Override public void run() {
        // Invalid warning, because inner class has to go trough the passed-in named parameter.
        // There's no name shadowing issue in this case, because of forced object access.
        String <warning descr="Local variable 'field' hides field in class 'Outer'">field</warning> = "hello";
        System.out.println(field);
        System.out.println(outer.field);
      }
    }.run();
  }
}