// LANGUAGE_VERSION: 1.6
// FIX: Simplify boolean expression
// AFTER-WARNING: The expression is unused
fun foo(y: Boolean) {
    <caret>3 != 3 && 2 > 1 || y
}

// IGNORE_K2 
// (see KT-73758)