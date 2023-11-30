// AFTER-WARNING: Parameter 'a' is never used
class Test {
    private val foo: Any<caret>

    init {
        foo = ""
        bar(foo)
    }
}

fun bar(a: Any) {
}