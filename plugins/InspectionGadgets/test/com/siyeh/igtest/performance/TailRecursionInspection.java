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

    private TailRecursionInspection getRootSO() {
        if (getParent() instanceof TailRecursionInspection)
        {
            return ((TailRecursionInspection) getParent()).getRootSO();
        }
        return this;
    }

    public Object getParent() {
        return null;
    }
}
class TailRecursion
{
    private boolean duplicate;
    private Something something;
    private TailRecursion original;

    public Something getSomething() {
        if (something == null) {
            if (isDuplicate()) {
                final TailRecursion recursion = getOriginal();
                return recursion.getSomething();
            } else {
                something = new Something();
            }
        }
        return something;
    }

    public Something foo() {
        if (!duplicate) {
            return something;
        } else {
            return getOriginal().foo();
        }
    }

    private TailRecursion getOriginal() {
        return original;
    }
    private boolean isDuplicate() {
        return duplicate;
    }

    public static class Something {}
}