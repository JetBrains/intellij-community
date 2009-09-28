class Usage {
    int m() {
        T2 test;
        return test.method(0, test.method(0) + 1);
    }
}