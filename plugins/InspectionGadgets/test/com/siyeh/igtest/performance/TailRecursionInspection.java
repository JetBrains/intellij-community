package com.siyeh.igtest.performance;

import java.awt.Container;
import java.io.IOException;

public class TailRecursionInspection {
    public TailRecursionInspection() {
    }

    public static int foo() throws IOException
    {
        return foo();
    }

    public int factorial(int val) {
        return factorial(val, 1);
    }

    private int factorial(int val, int runningVal) {
        if (val == 1) {
            return runningVal;
        } else {
            return factorial(val - 1, runningVal * val);
        }
    }

    private static boolean hasParent(Container child, Container parent) {
        if (child == null) {
            return false;
        }

        if (parent == null) {
            return true;
        }

        if (child.getParent() == parent) {
            return true;
        }

        return hasParent(child.getParent(), parent);
    }
}