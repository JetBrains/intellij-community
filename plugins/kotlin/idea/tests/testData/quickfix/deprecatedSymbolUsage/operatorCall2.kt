// "Replace with 'newFun(p)'" "true"

interface I

@Deprecated("", ReplaceWith("newFun(p)"))
operator fun I.plus(p: Int) {
    newFun(p)
}

fun I.newFun(p: Int){}

fun foo(i: I) {
    i <caret>+ 1
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix