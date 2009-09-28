package com.siyeh.igtest.abstraction;

import com.siyeh.igtest.abstraction2.SuperClass2;

public class CastWeakenTest extends SuperClass2 {
    public static void main(String[] args) {
        Object a = new CastWeakenTest();
        ((CastWeakenTest)a).getHandle();
    }

    protected Object getHandle() {
        return super.getHandle();
    }
}