interface A {
    void m1()
}

def a = new A() {
    @Override
    void m1() {
        println <selection>123</selection>
    }
}