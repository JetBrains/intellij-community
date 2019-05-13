interface A {
    void m1()
}

def a = new A() {
    def f

    @Override
    void m1() {
        f = 123
        println f<caret>
    }
}