/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.igtest.encapsulation;

public class UseOfAnotherObjectsPrivateField {
    public int foo;
    protected int bar;
    private int baz;

    public void fooBar(UseOfAnotherObjectsPrivateField copy)
    {
        foo = copy.foo;
        bar = copy.bar;
        baz = copy.baz;
        foo = this.baz;
        foo = baz;
    }

    class Inside {

        int f(UseOfAnotherObjectsPrivateField pugnacious) {
            return pugnacious.baz;
        }
    }
}
class SomewhereElse {

    void m(UseOfAnotherObjectsPrivateField tenacious) {
        tenacious.<warning descr="Direct access of non-public field 'bar' on another object">bar</warning> = 1;
        tenacious.foo = 2;
    }
}
