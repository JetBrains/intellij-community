// PROBLEM: none
// WITH_STDLIB
fun test(): String {
    <caret>with("") {
        return this@with
    }
}