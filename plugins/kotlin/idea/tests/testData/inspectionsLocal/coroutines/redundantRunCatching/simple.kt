// WITH_RUNTIME

fun foo() = runCatching<caret> { 42 }.getOrThrow()
