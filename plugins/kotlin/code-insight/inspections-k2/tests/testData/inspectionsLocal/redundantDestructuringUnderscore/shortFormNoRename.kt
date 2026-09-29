// "Remove redundant underscore entry" "true"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=complete
// K2_ERROR: NAME_BASED_DESTRUCTURING_UNDERSCORE_WITHOUT_RENAMING

data class Foo(val bar: String, val qux: Int)

fun test() {
    val (<caret>_, qux) = Foo("", 0)
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.codeInsight.inspections.declarations.RemoveRedundantDestructuringUnderscoreFix
