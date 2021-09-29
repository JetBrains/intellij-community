// WITH_RUNTIME
// AFTER-WARNING: Variable 's' is never used
fun test(list: List<Int>) {
    val s = <caret>"""foo ${list.joinToString(", ")} bar"""
}