package com.siyeh.igtest.style.unnecessary_parentheses;

import java.util.List;
import java.util.ArrayList;

public class UnnecessaryParenthesesInspection
{

    public int foo()
    {
        final String s = "foo" + (3 + 4); // do not warn here
        final String t = ("foo" + 3) + 4; // but warn here
        return (3); // warn
    }

    public void bar()
    {
        final int x = (3 + 4)*5; // no warn
        final int q = (3 + 4)-5; // warn
        final int p = 3 + (4-5); // warn!
        final int y = 3 + (4*5); // warn
        final int z = 3 + (4*(3*5)); // 2 warnings
        final int k = 4 * (3 * 5); // warn
        final int hash = ((this).hashCode()); // 2 warnings
        final int hash2 = (("x" + "y").hashCode()); // 1 warning
        List list = new ArrayList();
        (list.subList(1, 2)).get(0); // warn
    }

    public boolean testParenRedundancy(boolean a, boolean b, boolean c) {
        return a || (b || c); // warn
    }

    public int arg(int i,int j, int k) {
        int result = 4 - (3 - 1); // no warn!
        return i + (j + k); // warn
    }

    public boolean is(int value) {
        int i = (new Integer(33)).intValue(); // warn
        return value < 0 || value > 10
                || (value != 5); // warn 
    }

    public void commutative() {
        final int a = 1 - (2 - 3); // no warn;
        final int b = (1 - 2) - 3; // warn;
        System.out.println(1 << (1 << 2)); // no warn
    }

    public void instanceofTest(Object o) {
        boolean b;
        b = (o instanceof String);
        final boolean b1 = (b) ? (true) : false;
    }

    // http://www.jetbrains.net/jira/browse/IDEADEV-34926
    class ParenBug extends javax.swing.JPanel {
        private int resolution;
        private float pageWidth;  // in cm.
        private float pageHeight; // in cm.

        public void foo() {
            final float width = getSize().width; // actual width in dots
            final float height = getSize().height; // actual height in dots

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
      interface I {
        void foo(int x, int y);
      }
      interface J {
        void foo(int x);
      }

      {
        I i = (x, y) -> {};
        I i1 = (int x, int y) -> {};
  
        J j = (x) -> {};
        J j1 = (int x) -> {};
        J j2 = x -> {};
      }
    }

  void foo(Object a, boolean b) {
    final boolean c = b == (a != null);
    String s = "asdf" + (1 + 2 + "asdf");
    boolean d = c == (1 < 3);
  }

  private static boolean placeEqualsLastArg(Object place, Object[] args) {
    final Object[] objects = {(args.length - 1), 1};
    return args.length > 0 && place.equals(args[(args.length - 1)]);// here are unnecessary parentheses inside args[...]
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
      Runnable r = (()->true) ? () -> {} : () -> {}; // no warn
  }

  public java.util.function.IntFunction context() {
    return (a -> a)=1;
  }
}
