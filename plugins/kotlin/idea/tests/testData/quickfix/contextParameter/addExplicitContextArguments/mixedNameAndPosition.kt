// "Add name to argument: 's = "ctx"'" "false"
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xexplicit-context-arguments
// DISABLE_K2_ERRORS
context(s: String)
fun f(a: String = "", b: Int = 0) {}

fun test() {
    <caret>f("ctx", 1)
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.AddNameToArgumentFixFactory$AddNameToArgumentFix