package com.siyeh.igtest.j2me.field_repeatedly_accessed;

public class FieldRepeatedlyAccessed {

    String s = "";

    void <warning descr="Field 's' accessed repeatedly in method 'foo()'">foo</warning>() {
        while(true) {
            System.out.println(s);
        }
    }
}
