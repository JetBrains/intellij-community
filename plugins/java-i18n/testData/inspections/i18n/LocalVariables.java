class Foo {
    void foo() {
        String v1 = <warning descr="Hard coded string literal: \"text\"">"text"</warning>;
        @org.jetbrains.annotations.NonNls String v2 = "text";
        String v3;
        @org.jetbrains.annotations.NonNls String v4;

        v3 = <warning descr="Hard coded string literal: \"text\"">"text"</warning>;
        v4 = "text";
    }
}