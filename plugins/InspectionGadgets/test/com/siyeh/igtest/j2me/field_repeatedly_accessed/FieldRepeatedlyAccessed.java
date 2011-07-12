package com.siyeh.igtest.j2me.field_repeatedly_accessed;

public class FieldRepeatedlyAccessed {

    String s = "";

    void foo() {
        while(true) {
            System.out.println(s);
        }
    }
}
