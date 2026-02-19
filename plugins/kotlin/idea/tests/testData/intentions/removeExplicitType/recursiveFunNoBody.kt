// IS_APPLICABLE: false
// IGNORE_K1
fun foo(n: Int): <caret>Int = if (n < 0) 42 else foo(n - 1)