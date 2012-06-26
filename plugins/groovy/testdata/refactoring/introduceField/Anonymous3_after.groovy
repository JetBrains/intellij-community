interface A {
    void m1()
}

def a = new A() {
    int i
    {
        f = 123
    }

    def f

    @Override
    void m1() {
        println <selection>f</selection>
    }
}