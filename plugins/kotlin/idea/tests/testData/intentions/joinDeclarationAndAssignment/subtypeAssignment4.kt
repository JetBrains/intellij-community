// IS_APPLICABLE: false
class Test {
    private val foo: Any<caret>

    init {
        foo = ""
        bar(foo)
    }
}

fun bar(a: String) {
}