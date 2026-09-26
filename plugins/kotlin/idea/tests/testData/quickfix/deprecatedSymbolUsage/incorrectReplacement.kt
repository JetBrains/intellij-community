// "class org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix" "false"
// K2_ACTION: "class org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix" "false"

@Deprecated("", ReplaceWith("="))
fun oldFun() {
}

fun foo() {
    <caret>oldFun()
}
