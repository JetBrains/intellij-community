// WITH_STDLIB
// AFTER-WARNING: Variable 'x' is never used
fun test(s: String) {
    val x = s.<caret>count() > 0
}