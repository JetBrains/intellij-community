// WITH_STDLIB

fun foo() = runCatching<caret> { 42 }.getOrThrow()
