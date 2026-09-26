// WITH_STDLIB

fun foo() = runCatching<caret> { "" }.getOrThrow().length
