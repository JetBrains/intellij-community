// "Convert to positional destructuring syntax with square brackets" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// WITH_STDLIB

fun test() {
    val triple = Triple(1, "hello", true)
    val (<caret>x, y, z) = triple
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.declarations.ConvertNameBasedDestructuringShortFormToPositionalFix