class D {
    void f(int x, int y, boolean b) {
        final boolean sss = b ? <caret>x > y : x <= y;
    }
}