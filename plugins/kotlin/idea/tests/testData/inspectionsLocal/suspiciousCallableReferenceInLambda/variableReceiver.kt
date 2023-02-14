// WITH_STDLIB
class Test {
    val lambda = { s: String -> true }
    fun test() {
        "".let {<caret> lambda::invoke }
    }
}
