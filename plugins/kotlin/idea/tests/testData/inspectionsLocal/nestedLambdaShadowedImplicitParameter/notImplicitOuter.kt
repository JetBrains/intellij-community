// PROBLEM: none

class Foo {
    fun test() {
        run {
            val it = ""
            "".let { it<caret> }
            it
        }
    }
}
