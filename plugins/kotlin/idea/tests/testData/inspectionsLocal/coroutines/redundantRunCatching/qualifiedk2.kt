// WITH_STDLIB
// IGNORE_K1

fun foo() = kotlin.runCatching<caret> { 5 }.getOrThrow()
