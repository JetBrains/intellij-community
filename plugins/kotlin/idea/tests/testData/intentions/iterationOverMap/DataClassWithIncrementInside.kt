// IS_APPLICABLE: false
// WITH_STDLIB

data class XY(var x: Int, var y: Int)
fun test(xys: Array<XY>) {
    for (<caret>xy in xys) {
        xy.x++
        xy.y--
    }
}