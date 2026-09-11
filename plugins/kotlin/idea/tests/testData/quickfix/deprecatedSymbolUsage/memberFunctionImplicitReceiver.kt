// "Replace with 'newFun()'" "true"

class X {
    @Deprecated("", ReplaceWith("newFun()"))
    fun oldFun() {
        newFun()
    }

    fun newFun(){}
}

fun X.foo() {
    <caret>oldFun()
}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix