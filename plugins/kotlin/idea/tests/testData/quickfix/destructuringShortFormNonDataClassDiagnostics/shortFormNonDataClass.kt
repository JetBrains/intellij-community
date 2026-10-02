// "Convert to positional destructuring syntax with square brackets" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=name-mismatch
// WITH_STDLIB

class Point(val x: Int, val y: Int) {
    operator fun component1() = x
    operator fun component2() = y
}

fun test(p: Point) {
    val (<caret>x, y) = p
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ConvertToPositionalDestructuringFix
