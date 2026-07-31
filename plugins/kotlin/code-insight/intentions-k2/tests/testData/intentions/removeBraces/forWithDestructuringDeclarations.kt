// IS_APPLICABLE: false

data class Point(val x: Int, val y: Int)

fun foo(points: List<Point>) {
    for (point in points) {<caret>
        val (x, y) = point
    }
}
