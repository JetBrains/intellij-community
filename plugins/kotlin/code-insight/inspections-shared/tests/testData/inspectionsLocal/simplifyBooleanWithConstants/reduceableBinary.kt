// LANGUAGE_VERSION: 1.6
// FIX: Simplify boolean expression
// AFTER-WARNING: Parameter 'y' is never used
// AFTER-WARNING: The expression is unused
fun foo(y: Boolean) {
    <caret>2 > 1
}

// IGNORE_K2 
// (see KT-73758)