package com.siyeh.igtest.migration;

import java.util.Vector;
import java.util.Hashtable;
import java.util.Enumeration;
import java.util.Iterator;

public class EnumerationCanBeIterationInspection {

    static void foo(Vector v, Hashtable h) {
        Enumeration e = v.elements();
        while (e.hasMoreElements()) {
            System.out.println(e.nextElement());
        }
        Iterator i = v.iterator();
        while (i.hasNext()) {
            System.out.println(i.next());
        }
        e = h.elements();
        while (e.hasMoreElements()) {
            System.out.println(e.nextElement());
        }
        e = h.keys();
        i = h.values().iterator();
        while (i.hasNext()) {
            System.out.println(i.next());
        }
    }

    public void test(Vector vector) {

        for (Enumeration enumeration = vector.elements(); enumeration.hasMoreElements();) {
            Object a = enumeration.nextElement();
        }
    }

    public void before(Vector<String> vector) {

        final Enumeration<String> iterator = vector.elements();
        while (iterator.hasMoreElements()) {
            String s = iterator.nextElement();
        }
    }
}
