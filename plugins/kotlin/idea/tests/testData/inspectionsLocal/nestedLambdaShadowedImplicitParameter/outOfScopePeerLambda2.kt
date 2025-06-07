// PROBLEM: none
// Issue: KTIJ-32454
// WITH_STDLIB
class Foo {
    fun test() {
        run {
            "".let { it }
            "".let { it<caret> }
        }
    }
}
