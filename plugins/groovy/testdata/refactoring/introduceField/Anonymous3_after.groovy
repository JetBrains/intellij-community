interface A {
    void m1()
}

def a = new A() {
    int i
    def f
    {
        f = 123
    }

    @Override
    void m1() {
        println f<caret>
    }
}