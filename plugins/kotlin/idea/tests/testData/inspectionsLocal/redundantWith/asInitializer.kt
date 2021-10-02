// PROBLEM: none
// WITH_RUNTIME
// See KTIJ-19621
fun test(): Int = <caret>with("") {
    println()
    return 42
}