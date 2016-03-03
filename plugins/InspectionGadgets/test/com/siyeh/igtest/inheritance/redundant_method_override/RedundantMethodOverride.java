package redundant_method_override;

public class RedundantMethodOverride extends S {

  @Override
  void <warning descr="Method 'foo()' is identical to its super method">foo</warning>() {
    System.out.println();
  }

  void bar() {
    System.out.println();
  }

  public void m() {
    System.out.println();
  }
}
class S {

  void foo() {
    System.out.println();
  }

  synchronized void bar() {
    System.out.println();
  }

  void m() {
    System.out.println();
  }
}
class A {
  void f() {
    new X();
  }

  class X {}
}
class BB extends A {
  void f() {
    new X();
  }

  class X {}
}
class CC extends A {
  void <warning descr="Method 'f()' is identical to its super method">f</warning>() {
    new X();
  }
}
class SuperCall {
  void some() {
  }
}
class S2 extends SuperCall {
  @Override
  void <warning descr="Method 'some()' is identical to its super method">some</warning>() {
    super.some();
  }
}
class B {
  int some() {
    return 1;
  }
}
class C extends B {
  @Override
  int <warning descr="Method 'some()' is identical to its super method">some</warning>() {
    return super.some();
  }
}