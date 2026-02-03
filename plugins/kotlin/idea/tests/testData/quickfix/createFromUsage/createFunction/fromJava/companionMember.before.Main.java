// "Add method 'foo' to 'K'" "true"
// WITH_STDLIB
class J {
    void test() {
        boolean b = K.<caret>foo(1, "2");
    }
}
