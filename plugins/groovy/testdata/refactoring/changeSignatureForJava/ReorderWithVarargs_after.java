class Test {
    void foo(int a, String... s) {}

    {
        foo(1, "a", "bbb");
    }
}