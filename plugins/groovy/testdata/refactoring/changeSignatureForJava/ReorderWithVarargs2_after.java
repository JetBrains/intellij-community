class Test {
    static final String[] strs = new String[] { "a" };

    void <caret>foo(int a, String... s) {}

    {
        foo(1, strs);
    }
}