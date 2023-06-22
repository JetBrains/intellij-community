package com.siyeh.igtest.memory.inner_class_may_be_static;

import javax.swing.*;

public class InnerClassMayBeStatic {
     class <warning descr="Inner class 'Nested' may be 'static'"><caret>Nested</warning> {
         public void foo() {
             bar("InnerClassMayBeStaticInspection.this");
         }

         private void bar(String string) {
         }
     }
}

class IDEADEV_5513 {

    private static class Inner  {

        private boolean b = false;

        private class InnerInner {

            public void foo() {
                b = true;
            }
        }
    }
}

class C extends JComponent {
    private class I {
        public void foo() {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    repaint();
                }
            });
        }
    }
}
class D {

    void foo() {
        new Object() {
            class Y {}
        };
    }
}
class StaticInnerClass {

  private int foo;
  int bar;

  public class Baz extends StaticInnerClass  {
    Baz() {
      foo = -1;
    }
  }
  class <warning descr="Inner class 'C' may be 'static'">C</warning> extends StaticInnerClass {{
    bar = 1;
  }}
}
class SomeBeanUnitTest {

  private class <warning descr="Inner class 'BeanCreator' may be 'static'">BeanCreator</warning> {

    public BeanCreator  withQuery() {
      return null;
    }
  }
}
class Outer {
  class A { // may be static
    B b;
  }
  class B extends  A {} // may not be static

  class <warning descr="Inner class 'C' may be 'static'">C</warning> { // may be static
    D b;
    class D extends C {}
  }

  static class E {
    G.F b;
    class <warning descr="Inner class 'G' may be 'static'">G</warning> { // may be static
      class F extends  E {}
    }
  }

  class <warning descr="Inner class 'H' may be 'static'">H</warning> { // may be static
    J.I b;
    class J {
      class I extends  H {}
    }
  }
}
class Complex {
  class C {
    void m() {
      Complex.super.toString();
    }
  }
  int i;
  static void n() {
  }

  private class <warning descr="Inner class 'A' may be 'static'">A</warning> {
    private A() {
    }
  }

  class <warning descr="Inner class 'B' may be 'static'">B</warning> {
  }

  class <warning descr="Inner class 'F' may be 'static'">F</warning> extends Complex {
    class G {
    }

    {
      A a = (A) null;
      G g = (G) null;
      new A() {};
      new B();

      i = 10;
      new E().m();
      Complex.n();
    }

    void m(A a) {
      a.toString();
    }

    class E {
      private void m() {
      }
    }
  }
}
class Test1<T> {
  class Inner {
    private final T test;
    public Inner(T test) {
      this.test = test;
    }
  }
}
class Test2 {
  class <warning descr="Inner class 'Inner' may be 'static'">Inner</warning><T> {
    private final T test;
    public Inner(T test) {
      this.test = test;
    }
  }
}

class ImplicitConstructorReference {
  class A {
    C x = B::new;
  }

  interface C {
    B m();
  }

  class <warning descr="Inner class 'B' may be 'static'">B</warning> {}
}
class Scratch
{
  public static void main(String[] args)
  {
    class Inner
    {
      class Nested // can't be static
      {}
    }

  }
}
class JUnit5Test {
  @org.junit.jupiter.api.Nested
  class Inner {

  }
}
abstract class JavaClass<T> {
  public class <warning descr="Inner class 'InnerClass' may be 'static'">InnerClass</warning><M> {}

  public static <K, L> JavaClass<K>.InnerClass<L> baz(K t) {
    return null;
  }
}
class Simple {
  class <warning descr="Inner class 'Inner' may be 'static'">Inner</warning> {}

  void m() {
    new Inner();
  }

  static void s(Simple s) {
    s.new Inner();
  }
}
class X {
  X() {
    new Simple().new Inner();
  }
}
class Usage {

  {
    new Node(0, new Node(1, null));
  }

  private class <warning descr="Inner class 'Node' may be 'static'">Node</warning> {
    Node(int idx, Node next) {
    }
  }
}
class IdeaTest {

  public void test(){
    print(new InnerClass<Integer>().foo(Integer.valueOf(1)));
  }

  public void print(Integer foo){
    System.out.println(foo);
  }

  class <warning descr="Inner class 'InnerClass' may be 'static'">InnerClass</warning><T>{
    public T foo(T bar){
      return bar;
    }
  }
}
class C1 {
  public C1(Feedback i) {
  }
}

class Feedback {
  String getOutputWindowName() {
    return null;
  }
}

class A {

  protected class <warning descr="Inner class 'B' may be 'static'">B</warning> extends C1 {

    public B() {
      super(new Feedback() {
              public void outputMessage() {
                getOutputWindowName();
              }
            }
      );
    }
  }
}