// "Convert to positional destructuring syntax with square brackets" "false"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
// WITH_STDLIB
// K2_ERROR: NAME_BASED_DESTRUCTURING_UNDERSCORE_WITHOUT_RENAMING
// K2_AFTER_ERROR: NAME_BASED_DESTRUCTURING_UNDERSCORE_WITHOUT_RENAMING

class Point(val x: Int, val y: Int) {
    operator fun component1() = x
    operator fun component2() = y
}

fun test(x: Point) {
    (val _<caret>) = x
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeinsights.impl.base.quickFix.ConvertToPositionalDestructuringFix
