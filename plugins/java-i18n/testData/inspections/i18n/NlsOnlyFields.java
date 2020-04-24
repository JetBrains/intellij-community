import org.jetbrains.annotations.Nls;

class Foo {
    String field1 = "foo";
    @Nls String field2 = <warning descr="Hardcoded string literal: \"bar\"">"bar"</warning>;
    void foo() {
        field1 = "foo1";
        field2 = <warning descr="Hardcoded string literal: \"bar2\"">"bar2"</warning>;
    }
}