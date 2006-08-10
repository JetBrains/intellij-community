package com.siyeh.ig.initialization;

public class OverriddenMethodCallInConstructor extends Base {

    OverriddenMethodCallInConstructor() {
        foo();
    }
}
