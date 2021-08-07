// PROBLEM: Condition is always true
// FIX: none
data class Point(val x: Int, val y: Int) {
    fun test(p: Point) {
        if (p.y < p.x && p.x < x && x < y) {
            if (<caret>p.y < y) {

            }
        }
    }
}