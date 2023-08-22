// "Replace with 'newFun()'" "true"

class X {
    @Deprecated("", ReplaceWith("newFun()"))
    fun oldFun() {
        newFun()
    }

    fun newFun(){}
}

fun foo(x: X) {
    x.<caret>oldFun()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix