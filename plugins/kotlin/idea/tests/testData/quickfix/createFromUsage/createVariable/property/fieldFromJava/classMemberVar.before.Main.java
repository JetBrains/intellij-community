// "Add 'var' property 'foo' to 'K'" "true"
// WITH_STDLIB
class J {
    void test(K k) {
        String s = k.<caret>foo;
    }
}