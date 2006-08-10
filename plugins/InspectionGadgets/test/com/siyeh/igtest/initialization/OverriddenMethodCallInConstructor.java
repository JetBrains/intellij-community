package com.siyeh.igtest.initialization;

public class OverriddenMethodCallInConstructor extends Base {

    OverriddenMethodCallInConstructor() {
        foo();
    }
}
