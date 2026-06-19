// "Add explicit context argument" "false"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters -XXLanguage:-ExplicitContextArguments
// DISABLE_K2_ERRORS
context(c: String)
fun fq(a: Int = 5) {}

fun useQ() {
    fq<caret>()
}
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddExplicitContextArgumentFix