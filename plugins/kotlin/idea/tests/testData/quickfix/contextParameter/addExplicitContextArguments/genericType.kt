// "Add explicit context argument" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters -XXLanguage:+ExplicitContextArguments
// IGNORE_K1
// DISABLE_K2_ERRORS
context(t: T)
fun <T> use(): T = TODO()

fun main() {
    <caret>use<String>()
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitContextArgumentFix