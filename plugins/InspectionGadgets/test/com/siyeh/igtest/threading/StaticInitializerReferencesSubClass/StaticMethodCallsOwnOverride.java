class Super {
  static Super USED_PRIVATE = new <warning descr="Referencing subclass MyUsedSub from superclass Super initializer might lead to class loading deadlock">MyUsedSub</warning>();

  static void foo() {
    MyUsedSub.foo();
  }

  private static class MyUsedSub extends Super {
    static void foo() {
      System.out.println("hello");
    }

  }
}
