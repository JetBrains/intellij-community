// "Convert to a full name-based destructuring form" "false"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=name-mismatch
// WITH_STDLIB

data class Point(val x: Int, val y: Int)

fun b4(p: Point): Int {
    val (<caret>xVal, @Suppress("UNUSED_VARIABLE") unused) = p
    return xVal
}
