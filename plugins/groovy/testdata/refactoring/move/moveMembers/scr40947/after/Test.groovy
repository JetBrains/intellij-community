class A {

}

class Test {

    static void foo() {
        bar(); // note redundant "A" qualifier
    }

    static void bar() {}
}