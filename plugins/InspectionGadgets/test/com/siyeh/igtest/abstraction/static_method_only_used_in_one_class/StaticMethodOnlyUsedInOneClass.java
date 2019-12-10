package com.siyeh.igtest.abstraction;

public class StaticMethodOnlyUsedInOneClass {
    public static final String <warning descr="Static field 'CLOG' is only used from class 'OneClass'">CLOG</warning> = "";
    public static final StaticMethodOnlyUsedInOneClass INSTANCE = new StaticMethodOnlyUsedInOneClass();

    public static void <warning descr="Static method 'methodWithSomePrettyUniqueName()' is only used from class 'OneClass'">methodWithSomePrettyUniqueName</warning>() {

    }

    public void noUtilityClass() {}

    public static void x(int i) {}
    public static void x() {
        x(1);
    }

}
class UtilityClass {
    public static void x(int i) {}
    public static void x() {
        x(1);
    }
}
class OneClass {
    static {
        StaticMethodOnlyUsedInOneClass.methodWithSomePrettyUniqueName();
        StaticMethodOnlyUsedInOneClass.methodWithSomePrettyUniqueName();
        StaticMethodOnlyUsedInOneClass.methodWithSomePrettyUniqueName();
        StaticMethodOnlyUsedInOneClass.methodWithSomePrettyUniqueName();
        StaticMethodOnlyUsedInOneClass.methodWithSomePrettyUniqueName();
        System.out.println(StaticMethodOnlyUsedInOneClass.CLOG);
        System.out.println(StaticMethodOnlyUsedInOneClass.INSTANCE);
        UtilityClass.x();
        UtilityClass.x(2);
        StaticMethodOnlyUsedInOneClass.x();
        StaticMethodOnlyUsedInOneClass.x();
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
class Foo {
    void doSomething() {
        Bar bar = Bar.create();
        System.out.println(bar);
    }
}
class Bar {
    static Bar create() {
        return new Bar();
    }

    public void noUtilityClass() {
        System.out.println(State.SLEEPING);
    }
}
enum State {
    SLEEPING, AWAKE;
}