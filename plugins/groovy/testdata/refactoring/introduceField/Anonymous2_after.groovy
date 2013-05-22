interface A {
    void m1()
}

def a = new A() {
    {
        f = 123
    }
    int i
    def f

    @Override
    void m1() {
        println f<caret>
    }
}