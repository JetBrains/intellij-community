// "Add explicit context argument" "true"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters -XXLanguage:+ExplicitContextArguments
// IGNORE_K1
// DISABLE_K2_ERRORS
context(s: String)
fun bar(required: String): String = ""

fun foo() {
    <caret>bar("only-positional")
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitContextArgumentFix