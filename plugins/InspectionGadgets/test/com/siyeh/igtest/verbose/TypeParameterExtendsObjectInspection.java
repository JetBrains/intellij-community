package com.siyeh.igtest.verbose;

import java.util.List;

public class TypeParameterExtendsObjectInspection<E extends Object> {

    public <E extends Object>void foo(E e)
    {

    }
    public <E extends List>void foo2(E e)
    {

    }
}
