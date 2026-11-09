// "Rename variable to 'x'" "false"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax -XXLanguage:+FullValueClasses

value class Point(val x: Double, val y: Double)

fun test(point: Point) {
    point.let { (<caret>y, x) -> x + y }
}