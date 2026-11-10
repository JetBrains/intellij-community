// "Convert to a full name-based destructuring form" "false"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// WITH_STDLIB

data class Point(val x: Int, val y: Int)

fun b4(p: Point): Int {
    // // ISSUE: KT-82023 currently annotations are not supported on full form entries in name-based destructuring
    val (@Suppress("UNUSED_VARIABLE") <caret>unused, yVal) = p
    return yVal
}
