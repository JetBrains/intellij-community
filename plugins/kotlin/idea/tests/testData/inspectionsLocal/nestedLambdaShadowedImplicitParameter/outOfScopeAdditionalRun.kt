// PROBLEM: none
// WITH_STDLIB
class Foo {
    fun test() {
        "".let {
            "".let { it<caret> }
            run {
                "".let { it }
            }
        }
    }
}
