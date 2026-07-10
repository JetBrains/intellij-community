// "Convert to a full name-based destructuring form" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax

data class Point(val x: Int, val y: Int)

fun test(p: Point) {
    val (
        xVal, // coordinate X
        // smth else
        <caret>yVal // coordinate Y
    ) = p
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.ConvertNameBasedDestructuringShortFormToFullFix