class C {
    void <caret>method(boolean b, int... args) {
    }

    {
        method(true, 1,2);
        method(true, 1,2,3);
        method(true);
    }
}