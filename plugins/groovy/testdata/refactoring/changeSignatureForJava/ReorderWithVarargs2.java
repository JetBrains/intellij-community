class Test {
    static final String[] strs = new String[] { "a" };

    void <caret>foo(String[] s, int a) {}

    {
        foo(strs, 1);
    }
}