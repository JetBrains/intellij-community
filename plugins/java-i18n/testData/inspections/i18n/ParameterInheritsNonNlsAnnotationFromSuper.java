class A {
    void foo(@org.jetbrains.annotations.NonNls String p){

    }
}

class B extends A{
    void foo(String p){

    }
}

class C extends B{
    void foo(String p){
        foo("text");
    }
}