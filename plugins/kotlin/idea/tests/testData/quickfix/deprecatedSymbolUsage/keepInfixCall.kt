// "Replace with 'newFun(p)'" "true"

@Deprecated("", ReplaceWith("newFun(p)"))
infix fun String.oldFun(p: Int) {
    newFun(p)
}

infix fun String.newFun(p: Int) {
}

fun foo() {
    "" <caret>oldFun 1
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// IGNORE_K2