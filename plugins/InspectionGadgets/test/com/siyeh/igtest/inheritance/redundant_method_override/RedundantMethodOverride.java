import java.util.List;
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
  void <warning descr="Method 'some()' only delegates to its super method">some</warning>() {
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
  int <warning descr="Method 'some()' only delegates to its super method">some</warning>() {
    return super.some();
  }
}
@interface NotNull {}
class MyList<E> extends ArrayList<E> {
  @Override
  protected void <warning descr="Method 'removeRange()' only delegates to its super method">removeRange</warning>(int fromIndex, int toIndex) {
    super.removeRange(fromIndex, toIndex);
  }

  void m() {
    removeRange(0, 1);
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
/////////////////
class P {
  void f(boolean b, int i) {
    String a = "" + (a = "");
    new Object() {
      {
        // class initializer
      }
      void x(int i, int j) {
        i++;
        x(i, j);
      }
    };
    int z = 1;
    z++;

  }
}
class ABCD extends P {
  void <warning descr="Method 'f()' is identical to its super method">f</warning>(boolean b, int i) {
    String s = "" + (s = "");
    new Object() {
      {}

      void x(int k, /**/ final  int l) {
        k++;
        x(k, l);
      }
      // glucose & glutamine
    };
    int z1 = 1;
    z1++;
  }

}
//////////////////
class X1 {

  void x(Object o) {
    System.out.println(o);
    x(null);
  }
}
class X2 extends X1{
  void <warning descr="Method 'x()' is identical to its super method">x</warning>(Object o) {
    System.out.println(o);
    x(null);
  }
}
///////////
class X3 {
  void x() {
    List<Number> list = new ArrayList<>();
  }
}
class X4 extends X3 {
  @java.lang.Override
  void <warning descr="Method 'x()' is identical to its super method">x</warning>() {
    List<Number> list = new ArrayList<Number>();
  }
}
///////////
class X5 {
  Object x() {
    return new Object() {
      void a() {
        System.out.println(1);
      }
      void b() {
        System.out.println(2);
      }
    };
  }
}
class X6 extends X5 {
  @java.lang.Override
  Object <warning descr="Method 'x()' is identical to its super method">x</warning>() {
    return new Object() {
      void b(){ // whitespace difference

        System.out.println(2);
      }
      void a() {
        System.out.println(1);
      }
    };
  }
}
////////////////
class LocalModelGraphElementWrapper<T> {
  public T getElement() {
    return null;
  }
}
class LocalModelWrapper<T extends LocalModel> extends LocalModelGraphElementWrapper<T> {
  public T getElement()  { return super.getElement(); }
}
interface LocalModel {}
////////////////
class X9 {

  void x(@NotNull Object o) {
    x(null);
  }

  void x() {
    System.out.println();
    System.out.println();
    System.out.println();
  }
}
class X10 extends X9{
  void x(@NotNull Object o) {
    ((X2)o).x(null);
  }

  void <warning descr="Method 'x()' is identical to its super method">x</warning>() {
    {
      ;
      System.out.println();
      System.out.println();
    }
    System.out.println();
  }
}
class RedundantSuperBug {
  static class A {
    public void foo() {
      System.out.println(1);
    }
  }

  static class B extends A {
    @Override
    public void foo() {
      super.foo();
      System.out.println(1);
    }
  }

  static class C extends B {
    @Override
    public void foo() {
      super.foo();
      System.out.println(1);
    }
  }

  public static void main(String[] args) {
    new C().foo();
  }
}
class RedundantSuperBug2 {
  static class Super {
    public void foo() {
      System.out.println("From super");
    }
  }

  interface Iface {
    default void foo() {
      System.out.println("From iface");
    }
  }
  static class Sub extends Super implements Iface {
    @Override
    public void foo() {
      Iface.super.foo();
    }
  }

  static class Sub2 extends Super implements Iface {
    @Override
    public void <warning descr="Method 'foo()' only delegates to its super method">foo</warning>() {
      Sub2.super.foo();
    }
  }

  static class Sub3 implements Iface {
    @java.lang.Override
    public void <warning descr="Method 'foo()' only delegates to its super method">foo</warning>() {
      Iface.super.foo();
    }
  }

  interface Iface2 {
    default void foo() {
      System.out.println("From iface2");
    }
  }

  class X implements Iface, Iface2 {

    @Override
    public void foo() {
      Iface.super.foo();
    }
  }

  interface Iface3 extends Iface2 {
    default void foo() {
      System.out.println("From iface3");
    }
  }

  class Y implements Iface3 {
    @java.lang.Override
    public void <warning descr="Method 'foo()' only delegates to its super method">foo</warning>() {
      Iface3.super.foo();
    }
  }

  public static void main(String[] args) {
    new Sub().foo();
  }
}