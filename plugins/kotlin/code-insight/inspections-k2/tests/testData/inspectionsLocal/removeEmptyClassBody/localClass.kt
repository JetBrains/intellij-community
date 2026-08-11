fun test(foo: Any) {
    class Bar {<caret>}

    val x = (foo as? String)
}
