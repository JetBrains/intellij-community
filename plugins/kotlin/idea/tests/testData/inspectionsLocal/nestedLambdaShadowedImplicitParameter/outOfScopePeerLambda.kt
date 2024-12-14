// PROBLEM: none
// Issue: KTIJ-32454

class Foo {
    fun test() {
        run {
            "".let { it<caret> }
            "".let { it }
        }
    }
}
