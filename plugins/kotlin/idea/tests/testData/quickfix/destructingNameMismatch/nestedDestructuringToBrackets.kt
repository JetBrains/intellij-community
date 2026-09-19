// "Convert to positional destructuring syntax with square brackets" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// WITH_STDLIB

fun test(nested: Pair<Int, Pair<Int, Int>>) {
    val (x, <caret>y) = nested
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.declarations.ConvertNameBasedDestructuringShortFormToPositionalFix