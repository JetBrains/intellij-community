package one.two;

import one.two.KotlinClass.Companion.NestedObject;

public class UsageWithInstance {
    void t() {
        NestedObject.INSTANCE.staticFunction();
    }
}