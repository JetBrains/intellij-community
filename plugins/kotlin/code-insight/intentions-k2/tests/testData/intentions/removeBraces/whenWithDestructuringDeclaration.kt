// IS_APPLICABLE: false

data class Point(val x: Int, val y: Int)

fun foo(point: Point) {
    when {
        else -> {<caret>
            val (x, y) = point
        }
    }
}
