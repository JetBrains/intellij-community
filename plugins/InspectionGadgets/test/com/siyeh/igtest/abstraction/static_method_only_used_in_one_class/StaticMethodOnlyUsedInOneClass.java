package com.siyeh.igtest.abstraction;

public class StaticMethodOnlyUsedInOneClass {
    public static void <warning descr="Static method 'methodWithSomePrettyUniqueName()' is only used from class 'OneClass'">methodWithSomePrettyUniqueName</warning>() {

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

class Class1 {

    public static void main(String[] args) {
        System.out.println(Class2.getSomeTextUpper());
        Class2.main(args);
    }

}

class Class2 {
    private static String SOME_TEXT = "abcdef";

    public static String getSomeText() {
        return SOME_TEXT;
    }

    public static String getSomeTextUpper() {
        return SOME_TEXT.toUpperCase();
    }

    public static void main(String[] args) {
        System.out.println(getSomeText());
    }
}
class PrivateConstructor {

    private PrivateConstructor() {}

    public static PrivateConstructor build123() {
        return new PrivateConstructor();
    }
}
class User {

    void m() {
        PrivateConstructor.build123();
    }
}