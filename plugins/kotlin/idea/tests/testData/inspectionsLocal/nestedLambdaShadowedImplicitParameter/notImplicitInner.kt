// PROBLEM: none

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
