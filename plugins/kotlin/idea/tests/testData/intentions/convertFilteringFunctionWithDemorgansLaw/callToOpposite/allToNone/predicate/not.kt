// WITH_STDLIB
// AFTER-WARNING: Variable 'b' is never used
fun test(list: List<Int>) {
    val b = list.<caret>all { !it.isFoo }
}

private val Int.isFoo
    get() = true