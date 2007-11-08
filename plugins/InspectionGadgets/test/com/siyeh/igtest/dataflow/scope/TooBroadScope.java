package com.siyeh.igtest.dataflow.scope;

import java.util.ArrayList;
import java.util.Collection; import java.util.List;

public class TooBroadScope
{
    // Option "Only report variables that can be moved to inner blocks" is OFF
    public void test() {
        // Example #1
        {
            Collection<Integer> list  = null; //scope too broad
            {
                list = new ArrayList<Integer>();
                list.add(new Integer(0));
            }
        }

        // Example #2
        {

            Collection<Integer> list; // scope too broad
            list = new ArrayList<Integer>();
            list.add(new Integer(0));
        }

        // Example #3
        {

            Collection<Integer> list  = null; // nope
            list = new ArrayList<Integer>();
            list.add(new Integer(0));
        }
    }

    public void join() {
        String test;
        test = "asdf";
    }

    private int foo() {
        final int flim;
        final boolean bar = new java.util.Random().nextBoolean();
        if(bar) {
            flim = 42;
        } else {
            flim = 24;
        }
        return flim;
    }

    void foo(String a, String b, List c) {
        for (int d = 0, cannotNarrowMyScope; d < a.length(); d = cannotNarrowMyScope + b.length()) {
            cannotNarrowMyScope = a.indexOf(b, d);

            if (cannotNarrowMyScope < 0) {
                c.add(a.substring(d));
                break;
            } else {
                c.add(a.substring(d, cannotNarrowMyScope));
            }
        }
    }

    public int hashCode() {
        int result = 17;
        result = 37 * result;
        return result;
    }

    public void operatorAssignment() {
        int i = 10;
        i -= 1;
    }
}