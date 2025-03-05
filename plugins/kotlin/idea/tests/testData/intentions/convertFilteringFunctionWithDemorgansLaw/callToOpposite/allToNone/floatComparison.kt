// WITH_STDLIB
// AFTER-WARNING: Variable 'b' is never used
fun test(f: Float) {
    val b = listOf(f).all<caret> { it <= f }
}