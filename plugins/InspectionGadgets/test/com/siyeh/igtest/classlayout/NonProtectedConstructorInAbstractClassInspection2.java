package com.siyeh.igtest.classlayout;

public abstract class NonProtectedConstructorInAbstractClassInspection2 {
    protected NonProtectedConstructorInAbstractClassInspection2() {
        this(2);
    }

    private NonProtectedConstructorInAbstractClassInspection2(int foo) {
        super();
    }
}
