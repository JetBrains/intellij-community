// "Replace with 'newFun(p, this)'" "true"
// ERROR: 'infix' modifier is inapplicable on this function: must be a member or an extension function
// K2_AFTER_ERROR: 'infix' modifier is inapplicable to this function.

@Deprecated("", ReplaceWith("newFun(p, this)"))
infix fun String.oldFun(p: Int) {
    newFun(p, this)
}

infix fun newFun(p: Int, s: String){}

fun foo() {
    "" <caret>oldFun 1
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix