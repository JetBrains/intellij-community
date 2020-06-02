class Test {
  interface A {
    int foo();
  }
  interface B {
    int bar();
  }

  void test(Object obj) {
    boolean isA = obj instanceof A;
    if (!isA && !(obj instanceof B)) {
      return;
    }
    System.out.println(isA ? ((A) obj).foo() : ((B) obj).bar());
  }
}