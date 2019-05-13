package com.siyeh.igtest.threading.defaultRun;
class Test {
    {
        new Thread(() -> System.out.println("hello")).start();
    }
}