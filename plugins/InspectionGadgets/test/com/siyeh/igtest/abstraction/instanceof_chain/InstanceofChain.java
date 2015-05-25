package com.siyeh.igtest.abstraction.instanceof_chain;

public class InstanceofChain {
    void arg(Object o) {
        <warning descr="Chain of 'instanceof' checks indicates abstraction failure">if</warning> (o != null || o instanceof String || o instanceof String || o instanceof String) {

        } else if (o  instanceof  Integer) {

        } else if (o instanceof Boolean) {

        }
    }

    void m(boolean b, Object o) {
        <warning descr="Chain of 'instanceof' checks indicates abstraction failure">if</warning> (o instanceof String) {

        } else if (o instanceof Boolean) {

        } else if (b) {}
    }

    void n(Object o) {
        <warning descr="Chain of 'instanceof' checks indicates abstraction failure">if</warning> (o instanceof Integer) {}
        if (o instanceof Byte) {}
        if (o instanceof Long) {}
    }

    void f(Class objClass1, Class objClass2) {
        <warning descr="Chain of class equality checks indicates abstraction failure">if</warning> (objClass1 == String.class) {
            <warning descr="Chain of class equality checks indicates abstraction failure">if</warning> (objClass2 == Integer.class || objClass2 == Double.class) {
            } else if (objClass2 == Boolean.class) {
            }
        } else if (objClass1 == Byte.class){
            if (objClass2 == Float.class){
            }
        }
    }

    void g(Object obj1, Object obj2) {
        <warning descr="Chain of class equality checks indicates abstraction failure">if</warning> (obj1.getClass() == String.class) {
            <warning descr="Chain of class equality checks indicates abstraction failure">if</warning> (obj2.getClass() == Integer.class || obj2.getClass() == Double.class) {
            } else if (obj2.getClass() == Boolean.class) {
            }
        } else if (obj1.getClass() == Byte.class){
            if (obj2.getClass() == Float.class){
            }
        }
    }

    void h(Object o) {
        if (o instanceof String) {

        } else if (o == null) {

        }
    }
}
