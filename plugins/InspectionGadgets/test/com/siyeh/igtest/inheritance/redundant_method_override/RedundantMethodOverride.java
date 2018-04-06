import java.util.ArrayList;

public class RedundantMethodOverride extends S {

  @Override
  void <warning descr="Method 'foo()' is identical to its super method">foo</warning>() {
    System.out.println();;;;;
  }

  void bar() {
    System.out.println();
  }

  public void m() {
    System.out.println();
  }

  void n(int i) {
    super.n(10);
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

  void n(int i) {
    System.out.println(i);
  }
}
class A {
  void f() {
    new X();
  }

  void g() {}

  class X {}
}
class BB extends A {
  void f() {
    new X();
  }

  @Override
  final void g() {
    super.g();
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
@interface NotNull {}
class MyList<E> extends ArrayList<E> {
  @Override
  protected void <warning descr="Method 'removeRange()' is identical to its super method">removeRange</warning>(int fromIndex, int toIndex) {
    super.removeRange(fromIndex, toIndex);
  }

  void m() {
    removeRange(0, 1);
    new MyList2().removeRange(0, 0);
  }

  public boolean add(@NotNull E e) {
    return super.add(e);
  }
}
class MyList2 extends ArrayList {
  @Override
  protected void removeRange(int a, int b) {
    super.removeRange(a, b);
  }
}
////////////////
class Sup {
  void overload1(int i) {}
  void overload1(int i, boolean b) {}

  protected void foox() {
    overload1(0);
  }
}
class Sub extends Sup {
  @Override
  protected void foox() { // not redundant
    overload1(0, true);
  }
}
///////////////////////////////
class Params {
  int fooy(int param1, Object param2) {
    return param1 + param2.hashCode();
  }
}
class ComplexParameterEquivalent extends Params {
  @Override
  int <warning descr="Method 'fooy()' is identical to its super method">fooy</warning>(int p1, Object p2) {
    return ((p1) + p2.hashCode());
  }
}
/////////////////
@interface Anno {
  int value();
}
class Annotations1 {

  @Anno(1)
  void m() {}
}
class Annotations2 extends Annotations1{

  @Anno(2)
  @Override
  void m() {
    super.m();
  }
}
///////////////
interface XX {
  void x();
}
interface YY {
  default void x() {
    System.out.println();
  }
}
class ZZ implements YY, XX {
  @Override
  public void x() {
    YY.super.x();
  }
}
/////////////////
class QualifiedThis {

  QualifiedThis get() {
    return this;
  }

  QualifiedThis copy() {
    return new QualifiedThis() {
      @Override
      QualifiedThis get() {
        return QualifiedThis.this;
      }
    };
  }
}
////////////////
class Declaration {

  void localClass() {
    class One {
      void x() {}
    };
  }

  class Sub extends Declaration {
    @Override
    void localClass() {
      class Two {
        void y() {}
      };
    }
  }
}
//////////////
class DifferentAnonymous {

  Object x() {
    return new Object() {
      int one;
    };
  }
}
class DifferentAnonymous2 extends DifferentAnonymous {

  @Override
  Object x() {
    return new Object() {};
  }
}