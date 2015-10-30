package com.siyeh.igtest.abstraction;

public class StaticMethodOnlyUsedInOneClass {
    public static void <warning descr="static method 'methodWithSomePrettyUniqueName()' is only used from class 'OneClass'">methodWithSomePrettyUniqueName</warning>() {

    }

}
class OneClass {
    static {
        StaticMethodOnlyUsedInOneClass.methodWithSomePrettyUniqueName();
        StaticMethodOnlyUsedInOneClass.methodWithSomePrettyUniqueName();
        StaticMethodOnlyUsedInOneClass.methodWithSomePrettyUniqueName();
        StaticMethodOnlyUsedInOneClass.methodWithSomePrettyUniqueName();
        StaticMethodOnlyUsedInOneClass.methodWithSomePrettyUniqueName();
    }
}
class Main {
    public static void staticMethod() { }

    public void someMethod(Object o) {
        someMethod(new Object() {
            public void callbackMethod() {
                Main.staticMethod();
            }
        });
    }
}
