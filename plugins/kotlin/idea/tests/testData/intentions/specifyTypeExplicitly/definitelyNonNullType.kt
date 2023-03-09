// AFTER-WARNING: Variable 'y' is never used

fun <T> foo(x: T) {
    val <caret>y = x!!
}