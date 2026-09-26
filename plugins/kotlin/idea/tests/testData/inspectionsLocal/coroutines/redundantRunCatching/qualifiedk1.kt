// WITH_STDLIB
// IGNORE_K2

fun foo() = kotlin.runCatching<caret> { 5 }.getOrThrow()
