// "Rename variable to 'x'" "false"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax -XXLanguage:+FullValueClasses

value class Point(val x: Double, val y: Double)

value class Rectangle(val from: Point, val to: Point) {
    val area: Double
        get() {
            val (<caret>y, x) = from
            val (otherX = x, otherY = y) = to
            return Math.abs(x - otherX) * Math.abs(y - otherY)
        }
}