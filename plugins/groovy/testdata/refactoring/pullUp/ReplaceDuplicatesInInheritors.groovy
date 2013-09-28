class A {}
class AImpl1 extends A{
  void f<caret>oo() {
    System.out.println("hello");
    System.out.println("hello");
    System.out.println("hello");
    System.out.println("hello");
  }
}

class AImpl2 extends A {
  void bar() {
    System.out.println("hello");
    System.out.println("hello");
    System.out.println("hello");
    System.out.println("hello");
  }
}
