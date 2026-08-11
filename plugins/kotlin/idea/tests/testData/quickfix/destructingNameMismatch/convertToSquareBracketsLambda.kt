// "Convert to positional destructuring syntax with square brackets" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// WITH_STDLIB

fun test() {
    val block: (Pair<Int, String>) -> Unit = { (<caret>x, y) ->
        println(x)
    }
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.declarations.ConvertNameBasedDestructuringShortFormToPositionalFix