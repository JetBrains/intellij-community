fun test(foo: Any) {
    class Bar() {<caret>}

    (foo as? String)
}