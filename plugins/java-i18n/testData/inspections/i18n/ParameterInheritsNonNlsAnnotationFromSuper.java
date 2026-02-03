class A {
    String foo(@org.jetbrains.annotations.NonNls String p){
        return p;
    }
}

class B extends A{
    String foo(String p){
        return p;
    }
}

class C extends B{
    String foo(String p){
        return foo("text").substring(1);
    }

    void bar(C c) {
        c.foo("text");
        c.foo("text" + " " + "text");
        new C().foo(("text"));
    }
}