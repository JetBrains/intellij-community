class Foo {
    void foo() {
        String v1 = "text";
        @org.jetbrains.annotations.NonNls String v2 = "text";
        String v3;
        @org.jetbrains.annotations.NonNls String v4;

        v3 = "text";
        v4 = "text";
    }
}