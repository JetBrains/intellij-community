// WITH_STDLIB
// AFTER-WARNING: Variable 'i' is never used
// AFTER-WARNING: Variable 's' is never used
fun test() {
    val (i, s)<caret> = Pair(1, "s")
}