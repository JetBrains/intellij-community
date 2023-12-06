// PROBLEM: none

// With explicitly given type looks dangerous
fun test(): Int {
    val x = 1
    val <caret>y: Int = x
    return x + y
}