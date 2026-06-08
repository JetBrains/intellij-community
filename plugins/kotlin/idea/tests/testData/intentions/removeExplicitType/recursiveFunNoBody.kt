// IS_APPLICABLE: false

fun foo(n: Int): <caret>Int = if (n < 0) 42 else foo(n - 1)