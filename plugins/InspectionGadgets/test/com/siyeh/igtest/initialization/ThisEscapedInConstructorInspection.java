package com.siyeh.igtest.initialization;

import java.util.ArrayList;
import java.util.List;

public class ThisEscapedInConstructorInspection{
    private int foo = 3;

    public ThisEscapedInConstructorInspection(){
        super();
        final List list = new ArrayList(foo);
        list.add(this);
    }
}
