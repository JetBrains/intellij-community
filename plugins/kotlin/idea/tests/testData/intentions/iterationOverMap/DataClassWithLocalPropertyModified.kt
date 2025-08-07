// IS_APPLICABLE: false
// PROBLEM: none
// WITH_STDLIB

data class XY(val x: String, val y: Int)
fun test(xys: Array<XY>) {
    for (<caret>xy in xys) {
        val x = xy.x
        var y = xy.y
        println(x + y)
        y--
        println(y)
    }
}