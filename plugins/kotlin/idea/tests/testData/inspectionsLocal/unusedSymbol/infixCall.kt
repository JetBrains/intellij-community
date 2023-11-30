// PROBLEM: none
class Bar {
    private infix fun String.<caret>m(s: String) {}

    fun test() {
        "42" m "42"
    }
}