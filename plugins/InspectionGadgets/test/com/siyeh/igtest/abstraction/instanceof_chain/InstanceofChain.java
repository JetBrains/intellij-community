package com.siyeh.igtest.abstraction.instanceof_chain;

public class InstanceofChain {
    void arg(Object o) {
        if (o instanceof String || o instanceof String || o instanceof String) {

        } else if (o  instanceof  Integer) {

        } else if (o instanceof Boolean) {

        }
    }

    void m(boolean b, Object o) {
        if (o instanceof String) {

        } else if (o instanceof Boolean) {

        } else if (b) {}
    }

    void n(Object o) {
        if (o instanceof Integer) {}
        if (o instanceof Byte) {}
        if (o instanceof Long) {}
    }
}
