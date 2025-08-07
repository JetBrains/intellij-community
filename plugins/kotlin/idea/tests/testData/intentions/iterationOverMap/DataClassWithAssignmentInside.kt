// IS_APPLICABLE: false
// WITH_STDLIB
// PROBLEM: none

data class XY(var x: String, val y: String)
fun test(xys: Array<XY>) {
    for (<caret>xy in xys) {
        xy.x = xy.y
    }
}