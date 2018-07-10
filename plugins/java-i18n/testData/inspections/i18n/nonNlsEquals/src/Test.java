class Foo {
    void test(@org.jetbrains.annotations.NonNls String s, String s1) {
        if (s.equals("bar")) {}
        if (s1.equals("bar")) {}
        if ("bar".equals(s)) {}
        if ("bar".equals(s1)) {}
        if (("bar").equals((s))) {}
        if ((s).equals(("bar"))) {}
    }
}