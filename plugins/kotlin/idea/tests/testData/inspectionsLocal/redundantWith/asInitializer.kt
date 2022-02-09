// PROBLEM: none
// WITH_STDLIB
// See KTIJ-19621
fun test(): Int = <caret>with("") {
    println()
    return 42
}