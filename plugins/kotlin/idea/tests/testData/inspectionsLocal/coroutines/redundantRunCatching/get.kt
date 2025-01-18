// WITH_STDLIB
// PROBLEM: none
// K2-ERROR: Unresolved reference 'get'.

fun foo() = runCatching<caret> { 42 }.get()
