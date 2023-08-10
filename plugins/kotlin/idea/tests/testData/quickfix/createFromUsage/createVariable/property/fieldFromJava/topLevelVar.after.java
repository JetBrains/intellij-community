// "Add 'var' property 'foo' to 'TestKt'" "true"
// WITH_STDLIB
class J {
    void test() {
        String s = TestKt.<caret>foo;
    }
}