// "Add name to argument: 's = 42'" "false"
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// DISABLE_K2_ERRORS
context(s: String)
fun bar(a: Int): String = ""

fun foo() {
    <caret>bar(42)
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddNameToArgumentFixFactory$AddNameToArgumentFix