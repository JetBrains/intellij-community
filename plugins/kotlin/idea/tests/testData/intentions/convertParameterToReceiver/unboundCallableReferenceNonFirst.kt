// SHOULD_FAIL_WITH: Callable reference '::foo' will no longer match the expected function type

fun foo(n: Int, <caret>s: String): Boolean = true

val ref: (Int, String) -> Boolean = ::foo