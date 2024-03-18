// "Add method 'foo' to 'K'" "true"
class J {
    void test(K k) {
        boolean b = k.<selection><caret></selection>foo(1, "2");
    }
}
