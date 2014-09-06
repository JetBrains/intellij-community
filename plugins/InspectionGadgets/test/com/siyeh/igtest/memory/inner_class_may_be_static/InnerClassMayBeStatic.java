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
  class A { // may not be static
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
