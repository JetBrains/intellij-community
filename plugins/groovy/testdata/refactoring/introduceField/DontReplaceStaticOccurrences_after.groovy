class Bar {
    static int x = 5
    final f = x + 2

    def foo() {
        print f<caret>
    }

    static def bar() {
        print x + 2
    }
}