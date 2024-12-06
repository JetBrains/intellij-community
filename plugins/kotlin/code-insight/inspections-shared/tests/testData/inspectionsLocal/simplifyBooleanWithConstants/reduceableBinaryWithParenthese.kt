// LANGUAGE_VERSION: 1.6
// FIX: Simplify boolean expression
// AFTER-WARNING: Parameter 'y' is never used
fun foo(y: Boolean) {
    val x = 3
    <caret>x != x && (2 > 1 || y)
}

// IGNORE_K2 
// (see KT-73758)