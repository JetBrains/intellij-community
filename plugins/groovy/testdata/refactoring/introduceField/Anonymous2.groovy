interface A {
    void m1()
}

def a = new A() {
    int i

    @Override
    void m1() {
        println <selection>123</selection>
    }
}