// PROBLEM: none
interface Baz

fun test4(foo: Any) {
    class Bar: Baz;<caret>

    (foo as? String)?.length
}