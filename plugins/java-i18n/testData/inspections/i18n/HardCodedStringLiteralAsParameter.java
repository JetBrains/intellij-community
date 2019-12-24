class Foo {
    void foo(String s) {
        foo(<warning descr="Hardcoded string literal: \"text\"">"text"</warning>);
    }
}