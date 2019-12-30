import org.jetbrains.annotations.NonNls;

class A {
    A(@NonNls String name) {
    }
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
        B b1 = new B("name");
        B b2 = new B("name", <warning descr="Hardcoded string literal: \"lastName\"">"lastName"</warning>);
    }
}