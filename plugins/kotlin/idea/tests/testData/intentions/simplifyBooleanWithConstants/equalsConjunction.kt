// AFTER-WARNING: Parameter 'x' is never used
fun foo(x: Boolean): Boolean = <caret>true == (x && false)