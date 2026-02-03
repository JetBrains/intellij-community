// WITH_STDLIB
// AFTER-WARNING: Variable 'x' is never used
fun test(list: List<String>) {
    val x = list.<caret>count() > 0
}