// "Replace with 'newFun(p, p)'" "true"

@Deprecated("", ReplaceWith("newFun(p, p)"))
fun oldFun(p: Int) {
    newFun(p, p)
}

fun newFun(p1: Int, p2: Int){}

fun foo() {
    <caret>oldFun(bar()/*use bar()*/)
}

fun bar(): Int = 0

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix