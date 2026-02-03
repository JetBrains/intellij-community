// AFTER-WARNING: Parameter 's' is never used, could be renamed to _
// AFTER-WARNING: Variable 'p' is never used
fun test(i: Int) {
    val p: (String) -> Boolean =
        if (i == 1) { { <caret>s -> true } } else { { s -> false } }
}