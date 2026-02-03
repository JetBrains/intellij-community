// WITH_STDLIB
// PROBLEM: none

fun <T> Result<T>.getOrThrow(): Int = 5

fun foo() = runCatching<caret> { 42 }.getOrThrow()
