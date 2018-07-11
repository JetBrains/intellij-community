class Foo {
    void test(@org.jetbrains.annotations.NonNls String s, String s1) {
        if (s.equals("bar")) {}
        if (s1.equals(<warning descr="Hard coded string literal: \"bar\"">"bar"</warning>)) {}
        if ("bar".equals(s)) {}
        if (<warning descr="Hard coded string literal: \"bar\"">"bar"</warning>.equals(s1)) {}
        if (("bar").equals((s))) {}
        if ((s).equals(("bar"))) {}
    }
}