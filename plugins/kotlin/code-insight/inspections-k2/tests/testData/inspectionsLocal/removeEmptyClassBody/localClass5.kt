open class Baz

fun test5(foo: Any) {
    class Bar: Baz() /* comment */ {<caret>}

    (foo as? String)?.length
}
