class A {
    void foo() {
      System.out.println("hello");
      System.out.println("hello");
      System.out.println("hello");
      System.out.println("hello");
    }
}
class AImpl1 extends A{
}

class AImpl2 extends A {
  void bar() {
      foo();
  }
}
