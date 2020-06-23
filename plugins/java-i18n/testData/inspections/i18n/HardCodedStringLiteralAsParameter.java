class Foo {
    void foo(String s) {
        foo(<warning descr="Hardcoded string literal: \"text\"">"text"</warning>);
        foo(<warning descr="Hardcoded string literal: \"concatenated \"">"concatenated "</warning> + s + <warning descr="Hardcoded string literal: \" end\"">" end"</warning>);
    }
}