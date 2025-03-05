// WITH_STDLIB
// PROBLEM: none
// ERROR: This function must return a value of type Int
// K2_ERROR: Return type mismatch: expected 'Int', actual 'Unit'.

fun foo(a: Int) = kotlin.runCatching<caret> {
    if (a % 2 == 0) return
    5
}.getOrThrow()
