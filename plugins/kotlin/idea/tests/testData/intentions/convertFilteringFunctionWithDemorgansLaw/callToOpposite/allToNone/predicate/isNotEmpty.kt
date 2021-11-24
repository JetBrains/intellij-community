// WITH_STDLIB
// AFTER-WARNING: Variable 'b' is never used
fun test(list: List<String>) {
    val b = list.<caret>all { it.isNotEmpty() }
}