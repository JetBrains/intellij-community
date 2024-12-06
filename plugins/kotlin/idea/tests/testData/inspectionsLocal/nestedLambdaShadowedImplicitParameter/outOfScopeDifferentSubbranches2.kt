// PROBLEM: none

class Foo {
    private fun test() {
        run {
            "".let { it }
            run {
                "".let { it<caret> }
            }
        }
    }
}
