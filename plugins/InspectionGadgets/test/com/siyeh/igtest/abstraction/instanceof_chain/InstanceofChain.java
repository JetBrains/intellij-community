package com.siyeh.igtest.abstraction.instanceof_chain;

public class InstanceofChain {
    void arg(Object o) {
        if (o instanceof String || o instanceof String || o instanceof String) {

        } else if (o  instanceof  Integer) {

        } else if (o instanceof Boolean) {

        }
    }
}
