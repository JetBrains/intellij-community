class Bar {
    final f = "bar"

    def foo() {
        def f = 5
        print f
        print this.f<caret>
    }
}