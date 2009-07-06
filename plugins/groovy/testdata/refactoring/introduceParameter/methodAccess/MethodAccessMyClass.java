class Test {
    int method(int a, int b) {
        return <selection>anotherMethod(a + b)</selection>;
    }
    int i;

    int anotherMethod(int x) {
        return x;
    }
}
