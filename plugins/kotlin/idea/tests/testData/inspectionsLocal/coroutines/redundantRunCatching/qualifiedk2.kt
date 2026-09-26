// WITH_STDLIB


fun foo() = kotlin.runCatching<caret> { 5 }.getOrThrow()
