// WITH_RUNTIME
// AFTER-WARNING: Variable 's' is never used
fun test() {
    val (_, s) =<caret> Pair(1, "s")
}
