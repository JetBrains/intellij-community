// WITH_STDLIB
// AFTER-WARNING: Variable 'x' is never used
// AFTER-WARNING: Variable 'y' is never used

data class XY(val x: Int, val y: Int)

fun foo(list: List<XY>) {
    for (element<caret> in list) {
        val (x, y) = element
    }
}