class Foo {
    def bar = 5
    final f = bar + 5

    def foo() {
        print f<caret>
    }
}