// WITH_STDLIB
// AFTER-WARNING: Variable 'b' is never used
fun literalThen(a: Double) {
    val b: Double<caret> = when {
        a != 0.0 -> 1.0
        a == 0.0 -> 3.0
        else -> a
    }
}
