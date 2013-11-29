package com.siyeh.igtest.performance.inner_class_may_be_static;

import javax.swing.*;

public class InnerClassMayBeStaticInspection {
     class Nested {
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
  class C extends StaticInnerClass {{
    bar = 1;
  }}
}
class SomeBeanUnitTest {

  private class BeanCreator {

    public BeanCreator  withQuery() {
      return null;
    }
  }
}