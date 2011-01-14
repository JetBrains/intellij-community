package com.siyeh.igtest.jdk.auto_unboxing;



public class AutoUnboxing {
    {
        Long someNumber = Long.valueOf(0);

        long l = someNumber + 0;
        Long aLong = Long.valueOf(someNumber << 2);
    }
}
