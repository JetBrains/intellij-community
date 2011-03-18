class C {
    abstract void f() throws Exception;

    void m() {
        try {
            f();
        } <caret>catch (Exception ignore) { }
    }
}