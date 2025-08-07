// IS_APPLICABLE: false
// PROBLEM: none
// WITH_STDLIB

data class XY(val x: Int, val y: Int)

fun foo(list: List<XY>) {
    for (element<caret> in list) {
        val z = element.y
        val (x, y) = element
    }
}