// PROBLEM: none
// WITH_STDLIB
class Test {
    var s: String? = null

    fun test() {
        if (s != null) {
            <caret>requireNotNull(s)
        }
    }
}