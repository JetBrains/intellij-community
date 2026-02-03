// PROBLEM: none
// WITH_STDLIB
class Foo {
    fun test() {
        "".let {
            "".let {
                val it = ""
                it<caret>
            }
            it
        }
    }
}
