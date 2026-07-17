// "class org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix" "false"
// ERROR: Too many arguments for public fun oldFun(): Unit defined in root package in file callWithError.kt
// K2_AFTER_ERROR: TOO_MANY_ARGUMENTS
// K2_ERROR: TOO_MANY_ARGUMENTS

@Deprecated("", ReplaceWith("newFun()"))
fun oldFun() {
}

fun newFun(){}

fun foo() {
    <caret>oldFun(123)
}
