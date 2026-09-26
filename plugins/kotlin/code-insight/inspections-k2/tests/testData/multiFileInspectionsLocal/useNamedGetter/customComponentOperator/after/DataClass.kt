data class Point(val x: Int, val y: Int) {
    operator fun component3() = x + y
}