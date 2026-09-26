import org.jetbrains.annotations.NonNls;

class A {
    A(@NonNls String name) {
    }

    void foo(@NonNls String str) {}
}

class B extends A {
    B(String name) {
        super(name);
    }

    B(String name, String lastName) {
        this(name);
        System.out.println(lastName);
    }
}

class Test {
    {
        new B("name").foo("str");
        B b2 = new B("name", <warning descr="Hardcoded string literal: \"lastName\"">"lastName"</warning>);
    }
}