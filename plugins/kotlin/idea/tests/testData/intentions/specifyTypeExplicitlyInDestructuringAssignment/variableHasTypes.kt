// WITH_RUNTIME
// AFTER-WARNING: Variable 'i' is never used
// AFTER-WARNING: Variable 's' is never used
fun test() {
    val (i: Int, s) <caret>= Pair(1, "s")
}