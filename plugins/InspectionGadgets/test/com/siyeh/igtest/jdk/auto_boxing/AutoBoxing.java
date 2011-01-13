package com.siyeh.igtest.jdk.auto_boxing;




public class AutoBoxing {

    static {
        Long someNumber = 0L;
        Long aLong = someNumber << 2;
        someNumber++;
        someNumber = ~someNumber;
        someNumber = -someNumber;
        someNumber = +someNumber;
    }
}
