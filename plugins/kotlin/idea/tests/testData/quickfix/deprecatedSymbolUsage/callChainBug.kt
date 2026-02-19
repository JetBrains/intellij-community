// "Replace with 'newFun()'" "true"

@Deprecated("", ReplaceWith("newFun()"))
fun oldFun(): String = ""

fun newFun(): String = ""

fun foo() {
    val value = <caret>oldFun().length
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix