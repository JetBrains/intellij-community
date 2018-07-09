class Foo {
    private field1 = bar1()

    static def bar1() {
        ""
    }

    void bar2() {
        field1.toUpperCase()
        bar1().toUpperCase()
    }
}
