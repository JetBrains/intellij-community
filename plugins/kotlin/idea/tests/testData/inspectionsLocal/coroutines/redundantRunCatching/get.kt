// WITH_STDLIB
// PROBLEM: none

fun foo() = runCatching<caret> { 42 }.get()
