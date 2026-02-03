// "Add 'var' property 'foo' to 'K'" "true"
// WITH_STDLIB
class J {
    void test() {
        String s = K.<caret>foo;
    }
}