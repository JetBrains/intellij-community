package com.siyeh.igtest.abstraction.instanceof_chain;

public class InstanceofChain {
    void arg(Object o) {
        <warning descr="Chain of 'instanceof' checks indicates abstraction failure">if</warning> (o instanceof String || o instanceof String || o instanceof String) {

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
}
