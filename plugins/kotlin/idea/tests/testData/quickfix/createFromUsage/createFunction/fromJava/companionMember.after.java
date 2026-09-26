// "Add method 'foo' to 'K'" "true"
// WITH_STDLIB
class J {
    void test() {
        boolean b = K.<selection><caret></selection>foo(1, "2");
    }
}
