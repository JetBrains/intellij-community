package com.siyeh.igtest.style.unnecessary_parentheses;

import java.util.List;
import java.util.ArrayList;

public class UnnecessaryParenthesesInspection
{

  void concatenations() {
    System.out.println("a" + <warning descr="Parentheses around '(1 + \"b\" + \"c\")' are unnecessary">(1 + "b" + "c")</warning>);
    System.out.println("a" + (1 + 2 + "b" + "c"));
    System.out.println("a" + <warning descr="Parentheses around '(\"b\" + 1 + 2 + \"c\")' are unnecessary">("b" + 1 + 2 + "c")</warning>);

    // test no exception on incomplete code
    System.out.println("a" + ("b" +<error descr="Expression expected"> </error>));
  }

    void switchExpressions() {
      String s = (switch(1) {
        case 1 -> "one";
        default -> "other";
      }).substring(1);
      int z = -<warning descr="Parentheses around '(switch(1) { default -> 10; })' are unnecessary">(switch(1) {
        default -> 10;
      })</warning> + 10;
    }

    public int foo()
    {
        final String s = "foo" + (3 + 4); // do not warn here
        final String t = <warning descr="Parentheses around '(\"foo\" + 3)' are unnecessary">("foo" + 3)</warning> + 4; // but warn here
        return <warning descr="Parentheses around '(3)' are unnecessary">(3)</warning>; // warn
    }

    public void bar()
    {
        final int x = (3 + 4)*5; // no warn
        final int q = <warning descr="Parentheses around '(3 + 4)' are unnecessary">(3 + 4)</warning>-5; // warn
        final int p = 3 + <warning descr="Parentheses around '(4-5)' are unnecessary">(4-5)</warning>; // warn!
        final int y = 3 + <warning descr="Parentheses around '(4*5)' are unnecessary">(4*5)</warning>; // warn
        final int z = 3 + <warning descr="Parentheses around '(4*(3*5))' are unnecessary">(4*<warning descr="Parentheses around '(3*5)' are unnecessary">(3*5)</warning>)</warning>; // 2 warnings
        final int k = 4 * <warning descr="Parentheses around '(3 * 5)' are unnecessary">(3 * 5)</warning>; // warn
        final int hash = <warning descr="Parentheses around '((this).hashCode())' are unnecessary">(<warning descr="Parentheses around '(this)' are unnecessary">(this)</warning>.hashCode())</warning>; // 2 warnings
        final int hash2 = <warning descr="Parentheses around '((\"x\" + \"y\").hashCode())' are unnecessary">(("x" + "y").hashCode())</warning>; // 1 warning
        List list = new ArrayList();
        <warning descr="Parentheses around '(list.subList(1, 2))' are unnecessary">(list.subList(1, 2))</warning>.get(0); // warn
    }

    public boolean testParenRedundancy(boolean a, boolean b, boolean c) {
        return a || <warning descr="Parentheses around '(b || c)' are unnecessary">(b || c)</warning>; // warn
    }

    public int arg(int i,int j, int k) {
        int result = 4 - (3 - 1); // no warn!
        return i + <warning descr="Parentheses around '(j + k)' are unnecessary">(j + k)</warning>; // warn
    }

    public boolean is(int value) {
        int i = <warning descr="Parentheses around '(new Integer(33))' are unnecessary">(new Integer(33))</warning>.intValue(); // warn
        return value < 0 || value > 10
                || <warning descr="Parentheses around '(value != 5)' are unnecessary">(value != 5)</warning>; // warn 
    }

    public void commutative() {
        final int a = 1 - (2 - 3); // no warn;
        final int b = <warning descr="Parentheses around '(1 - 2)' are unnecessary">(1 - 2)</warning> - 3; // warn;
        System.out.println(1 << (1 << 2)); // no warn
    }

    public void instanceofTest(Object o) {
        boolean b;
        b = <warning descr="Parentheses around '(o instanceof String)' are unnecessary">(o instanceof String)</warning>;
        final boolean b1 = (b) ? <warning descr="Parentheses around '(true)' are unnecessary">(true)</warning> : false;
    }

    // http://www.jetbrains.net/jira/browse/IDEADEV-34926
    class ParenBug extends <error descr="Cannot resolve symbol 'javax'">javax</error>.swing.JPanel {
        private int resolution;
        private float pageWidth;  // in cm.
        private float pageHeight; // in cm.

        public void foo() {
            final float width = <error descr="Cannot resolve method 'getSize' in 'ParenBug'">getSize</error>().width; // actual width in dots
            final float height = <error descr="Cannot resolve method 'getSize' in 'ParenBug'">getSize</error>().height; // actual height in dots

            // to determine the ratio, do the following:
            final float ratio;
            if (pageWidth != -1) {
                ratio = pageWidth / (width / (float) resolution * (float) 2.54); // should not warn here
            } else {
                ratio = pageHeight / (height / (float) resolution * (float) 2.54);
            }

            System.out.println("resolution: X=" + resolution);
            System.out.println("page_width (cm): " + pageWidth);
            System.out.println("ratio=" + ratio);
            System.out.println(64 / (2 * 16 / 4 * 2 * 2));
        }
    }

    class ParenthesesAroundLambda {
      <error descr="Static declarations in inner classes are not supported at language level '15'">interface I</error> {
        void foo(int x, int y);
      }
      <error descr="Static declarations in inner classes are not supported at language level '15'">interface J</error> {
        void foo(int x);
      }

      {
        I i = (x, y) -> {};
        I i1 = (int x, int y) -> {};
  
        J j = <warning descr="Parentheses around '(x)' are unnecessary">(x)</warning> -> {};
        J j1 = (int x) -> {};
        J j2 = x -> {};
      }
    }

  void foo(Object a, boolean b) {
    final boolean c = b == (a != null);
    String s = "asdf" + (1 + 2 + "asdf");
    boolean d = c == <warning descr="Parentheses around '(1 < 3)' are unnecessary">(1 < 3)</warning>;
  }

  private static boolean placeEqualsLastArg(Object place, Object[] args) {
    final Object[] objects = {<warning descr="Parentheses around '(args.length - 1)' are unnecessary">(args.length - 1)</warning>, 1};
    return args.length > 0 && place.equals(args[<warning descr="Parentheses around '(args.length - 1)' are unnecessary">(args.length - 1)</warning>]);// here are unnecessary parentheses inside args[...]
  }

  void uu() {
    Object info  = new Object[]{"abc"};
    String s = (String)((Object[])info)[0];
  }

  void zz() {
    int a = 10;
    int b = 20;

    final int i = a * ((b + 2) / 3); // no warn
    final int j = a * ((b + 2) % 3); // no warn
  }

  void lambda() {
      Runnable r = (<error descr="boolean is not a functional interface">()->true</error>) ? () -> {} : () -> {}; // no warn
  }

  public java.util.function.IntFunction context() {
    return <error descr="Incompatible types. Found: 'int', required: '<lambda expression>'">(a -> a)=1</error>;
  }
}
class A{

  A() {
    ((<error descr="Expression expected"><</error><error descr="Cannot resolve symbol 'x'">x</error>><error descr="Expression expected">)</error><EOLError descr="';' expected"></EOLError>
  }
}
