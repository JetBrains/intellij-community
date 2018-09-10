class Foo {
    void foo(String s) {
        foo(<warning descr="Hard coded string literal: \"text\"">"text"</warning>);
    }
}