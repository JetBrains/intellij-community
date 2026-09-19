// "Convert to positional destructuring syntax with square brackets" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// WITH_STDLIB

fun test() {
    val list = listOf("a", "b", "c")
    for ((<caret>i, element) in list.withIndex()) {
        println("$i: $element")
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.declarations.ConvertNameBasedDestructuringShortFormToPositionalFix