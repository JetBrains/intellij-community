class Test {
    void foo() {
        @org.jetbrains.annotations.NonNls StringBuffer buffer = new StringBuffer("text");
        buffer = new StringBuffer("text");

        @org.jetbrains.annotations.NonNls StringBuffer parenthesized = (new StringBuffer("text"));
    }
}