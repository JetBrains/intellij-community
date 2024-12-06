// PROBLEM: none

class Foo {
    private fun test() {
        run {
            "".let { it<caret> }
            run {
                "".let { it }
            }
        }
    }
}
