interface I {
  def foo(int x<error descr="Default initializers are not allowed in abstract methods"> = 5</error>)
}

abstract class A {
  abstract foo(int x = 5)

  def abr(int x = 5){}
}

enum E {
  ONE {
    def foo(x) {}
  };

  abstract foo(x = 3)
  def bar(y = 12) {}
}

@interface An {
  int foo()
  String bar(x = 2)
}

def a = new Runnable() {
  void run() {}
  <error descr="Anonymous class cannot have abstract method">abstract</error> foo(x = 5);
}

enum EE {
  T {
    def foo() {}
    <error descr="Anonymous class cannot have abstract method">abstract</error> bar(x = 4);
  };

  def foo() {}
}