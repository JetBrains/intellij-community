package com.siyeh.igtest.abstraction;

public class StaticMethodOnlyUsedInOneClass {
    public static void method() {

    }

}
class OneClass {
    static {
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
        StaticMethodOnlyUsedInOneClass.method();
    }
}