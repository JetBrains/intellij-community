// "Add name to argument: 'i = 1'" "false"
// COMPILER_ARGUMENTS: -XXLanguage:+ContextParameters -XXLanguage:+ExplicitContextArguments
// IGNORE_K1
// DISABLE_K2_ERRORS
context(i: Int, s: String)
fun bar(x: Int, y: String): String = ""

fun foo() {
    <caret>bar(i = 1, s = "hi", x = 42, y = "hello")
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddNameToArgumentFixFactory$AddNameToArgumentFix