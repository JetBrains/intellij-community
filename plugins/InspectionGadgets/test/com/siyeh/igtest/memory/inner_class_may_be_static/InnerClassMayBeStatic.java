package com.siyeh.igtest.memory.inner_class_may_be_static;

import javax.swing.*;

public class InnerClassMayBeStatic {
     class <warning descr="Inner class 'Nested' may be 'static'">Nested</warning> {
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
  class <warning descr="Inner class 'A' may be 'static'">A</warning> { // may be static
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