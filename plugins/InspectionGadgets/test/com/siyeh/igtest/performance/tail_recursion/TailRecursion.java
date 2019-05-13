package com.siyeh.igtest.performance;

import java.awt.Container;
import java.io.IOException;

public class TailRecursion {
    public TailRecursion() {
    }

    public static int foo() throws IOException
    {
        return <warning descr="Tail recursive call 'foo()'">foo</warning>();
    }

    public int factorial(int val) {
        return factorial(val, 1);
    }

    private int factorial(int val, int runningVal) {
        if (val == 1) {
            return runningVal;
        } else {
            return <warning descr="Tail recursive call 'factorial()'">factorial</warning>(val - 1, runningVal * val);
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

        return <warning descr="Tail recursive call 'hasParent()'">hasParent</warning>(child.getParent(), parent);
    }

    private TailRecursion getRootSO() {
        if (getParent() instanceof TailRecursion)
        {
            return ((TailRecursion) getParent()).<warning descr="Tail recursive call 'getRootSO()'">getRootSO</warning>();
        }
        return this;
    }

    public Object getParent() {
        return null;
    }
}
class TailRecursion2
{
    private boolean duplicate;
    private Something something;
    private TailRecursion2 original;

    public Something getSomething() {
        if (something == null) {
            if (isDuplicate()) {
                final TailRecursion2 recursion = getOriginal();
                return recursion.<warning descr="Tail recursive call 'getSomething()'">getSomething</warning>();
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
            return getOriginal().<warning descr="Tail recursive call 'foo()'">foo</warning>();
        }
    }

    private TailRecursion2 getOriginal() {
        return original;
    }
    private boolean isDuplicate() {
        return duplicate;
    }

    public static class Something {}
}
class IdeaBug2 {
    interface Bug {
        default boolean isThisBug(String name) {return this.getBugContainer().isThisBug(name);}
        BugContainer getBugContainer();
    }
    interface BugContainer extends Bug {}
    // not needed:
    class MyBugContainer implements BugContainer {
        @Override public boolean isThisBug(String name) {return false;}
        @Override public BugContainer getBugContainer() {return this;}
    }
    class MyBug implements Bug {
        @Override public BugContainer getBugContainer() {return new MyBugContainer();}
    }
}