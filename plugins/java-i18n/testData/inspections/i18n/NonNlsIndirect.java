class A {
    String foo(@org.jetbrains.annotations.NonNls String p){
        return p;
    }
}

class B {
    void foo(A a) {
        a.foo("abc");
        String s;
        s = "abc";
        a.foo(s);
    }
}