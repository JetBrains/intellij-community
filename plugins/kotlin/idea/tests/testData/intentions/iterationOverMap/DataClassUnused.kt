// WITH_STDLIB
// AFTER-WARNING: Variable 'x' is never used
// AFTER-WARNING: Variable 'y' is never used

data class XY(val x: String, val y: String)
fun test(xys: Array<XY>) {
    for (<caret>xy in xys) {}
}