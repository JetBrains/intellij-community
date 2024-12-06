// PROBLEM: none

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
