// "Replace with 'newFun()'" "true"
// WITH_STDLIB

class C {
    @Deprecated("", ReplaceWith("newFun()"))
    fun oldFun() {
        newFun()
    }
}

fun newFun(){}

fun foo() {
    getC()?.<caret>oldFun()
}

fun getC(): C? = null

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix