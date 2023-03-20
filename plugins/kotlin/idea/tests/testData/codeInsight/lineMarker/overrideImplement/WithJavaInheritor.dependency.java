class Impl implements A {
    @Override
    public void f() {}
}
class Impl2 {
    void m() {
        new A() {
            @Override
            public void f() {}
        }
    }
}