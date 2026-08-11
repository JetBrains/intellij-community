open class Baz

fun test5(foo: Any) {
    class Bar: Baz();<caret>

    (foo as? String)?.length
}
