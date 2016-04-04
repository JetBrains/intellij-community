package com.siyeh.igtest.controlflow.if_may_be_conditional;

public class IfMayBeConditional {

    void foo(int a, int b) {
        int c = 0;
        if (a < b) { c += a - b; } else { c -= b; }
    }

    void foo2(int a, int b) {
        int c = 0;
        <warning descr="'if' could be replaced with conditional expression">if</warning> (a < b) { c += a - b; } else { c += b; }
    }

    void foo3(int i, StringBuilder sb) {
        <warning descr="'if' could be replaced with conditional expression">if</warning> (i == 0) {
            sb.append("type.getConstructor()", 0, 1);
        }
        else {
            sb.append("DescriptorUtils.getFQName(cd)",0, 1);
        }
    }

    int foo4(int a, int b) {
        <warning descr="'if' could be replaced with conditional expression">if</warning> (a < b) return a;
        else return b;
    }

    int foo5(int a, int b, int c) {
        if (a < b) {
            return a;
        } else {
            return b < c ? b : c;
        }
    }

    void foo6(int a, int b, int c) {
      int i;
      if (a < b) {
          i = a;
      } else {
          i = b < c ? b : c;
      }
    }

    void largeIf(boolean a, boolean b, boolean c) {
        final String value;
        if (a) {
            value = "a";
        } else if (b) {
            value = "b";
        } else if (c) {
            value = "c";
        } else {
            value = "d";
        }
    }
}
