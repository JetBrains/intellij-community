class Foo {
    @org.jetbrains.annotations.NonNls String field;
    @org.jetbrains.annotations.NonNls String field2 = "text1";
    void foo() {
        field = "text2";
    }
}