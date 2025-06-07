// PROBLEM: none
// WITH_STDLIB
class Foo {
    fun test() {
        run {
            val it = ""
            "".let { it<caret> }
            it
        }
    }
}
