// "Replace with 'newFun1(newFun2())'" "true"

class X {
    @Deprecated("", ReplaceWith("newFun1(newFun2())", ""))
    fun oldFun() {
        newFun1(newFun2())
    }

    fun newFun1(p: Int): Int = p
    fun newFun2(): Int = 1
}

fun foo(x: X) {
    x.<caret>oldFun()
}

// IGNORE_K1
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix