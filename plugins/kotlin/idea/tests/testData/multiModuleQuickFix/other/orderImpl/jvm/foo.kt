// "Replace with 'actual'" "true"
// IGNORE_K2

<caret>impl public tailrec fun foo(n: Int): Int = if (n < 2) n else foo(n - 1)