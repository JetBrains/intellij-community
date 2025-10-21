// IS_APPLICABLE: false
// WITH_STDLIB
// PROBLEM: none

data class XY(val x: String, val y: String)
fun test(xys: Array<XY?>) {
    for (<caret>xy in xys) {
        val x = xy?.x
        val y = xy?.y
        println(x + y)
    }
}