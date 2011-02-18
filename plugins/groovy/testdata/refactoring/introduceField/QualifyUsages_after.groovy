class Bar {
    final def f = "bar"

    def foo() {
        def f = 5
        print f
        print this.<selection>f</selection>
    }
}