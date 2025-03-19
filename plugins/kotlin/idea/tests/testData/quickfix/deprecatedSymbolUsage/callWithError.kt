// "class org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix" "false"
// ERROR: Too many arguments for public fun oldFun(): Unit defined in root package in file callWithError.kt
// K2_AFTER_ERROR: Too many arguments for 'fun oldFun(): Unit'.

@Deprecated("", ReplaceWith("newFun()"))
fun oldFun() {
}

fun newFun(){}

fun foo() {
    <caret>oldFun(123)
}
