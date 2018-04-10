class Foo {
    int field1

    void bar1() {
        this.field1
        this.bar2()
    }

    void bar2() {
        this.field2
    }

    void bar3() {
        this.bar4()
    }

    void bar5(String[] a) {
        a.bar6 {it.toUpperCase()}
    }

    void bar6(Object[] a, Closure<Object> c) {
    }
}
