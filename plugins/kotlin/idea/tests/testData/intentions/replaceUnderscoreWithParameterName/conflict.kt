// WITH_STDLIB
// AFTER-WARNING: Variable 'first' is never used
// AFTER-WARNING: Variable 'first1' is never used

fun foo(pair: Pair<Int, Int>) {
    val (<caret>_, _) = pair
    val first = 42
}