// "Add explicit context arguments" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters -XXLanguage:+ExplicitContextArguments
// IGNORE_K1
// DISABLE_K2_ERRORS
context(first: String, second: String)
fun greet(): String = ""

fun main() {
    <caret>greet("hello", "world")
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitContextArgumentFix