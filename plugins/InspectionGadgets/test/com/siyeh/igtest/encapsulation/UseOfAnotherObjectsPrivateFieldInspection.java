package com.siyeh.igtest.encapsulation;

public class UseOfAnotherObjectsPrivateFieldInspection {
    public int foo;
    protected int bar;
    private int baz;

    public void fooBar(UseOfAnotherObjectsPrivateFieldInspection copy)
    {
        foo = copy.foo;
        bar = copy.bar;
        baz = copy.baz;
        foo = this.baz;
        foo = baz;
    }
}
