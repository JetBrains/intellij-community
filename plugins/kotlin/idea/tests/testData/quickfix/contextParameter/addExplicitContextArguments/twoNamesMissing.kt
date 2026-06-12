// "Add explicit context arguments" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters -XXLanguage:+ExplicitContextArguments
// IGNORE_K1
// DISABLE_K2_ERRORS
context(x: String, y: Int)
fun foo2(a: String): String = x + a

fun main() {
    <caret>foo2("Hello", 0, a = "World")
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitContextArgumentFix