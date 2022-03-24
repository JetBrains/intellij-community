// PROBLEM: none
// WITH_STDLIB
fun foo(i: Int) {}

fun test() {
    foo(<caret>with("") {
        println()
        1
    })
}