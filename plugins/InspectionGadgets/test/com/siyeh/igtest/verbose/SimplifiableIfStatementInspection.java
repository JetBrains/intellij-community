package com.siyeh.igtest.verbose;

public class SimplifiableIfStatementInspection {
    public void foo() {
        boolean a = bar();
        boolean b = bar();
        final boolean i;
        if (a) {
            i = b;
        } else {
            i = true;
        }
        final boolean j;
        if (a) {
            j = true;
        } else {
            j = b;
        }
        final boolean k;
        if (a) {
            k = b;
        } else {
            k = false;
        }
        final boolean l;
        if (a) {
            l = false;
        } else {
            l = b;
        }
    }

    private boolean bar(){
        return true;
    }

    public boolean foo1() {
        boolean a = bar();
        boolean b = bar();
        if (a) {
            return b;
        } else {
            return true;
        }
    }

    public boolean foo2() {
        boolean a = bar();
        boolean b = bar();
        if (a) {
            return true;
        } else {
            return b;
        }
    }

    public boolean foo3() {
        boolean a = bar();
        boolean b = bar();
        if (a) {
            return false;
        } else {
            return b;
        }
    }

    public boolean foo4() {
        boolean a = bar();
        boolean b = bar();
        if (a) {
            return b;
        } else {
            return false;
        }
    }
}