// "Import function 'RegObject.regularFun'" "true"
package p

open class Reg {
    fun regularFun() {}
}

object RegObject : Reg()

fun usage() {
    <caret>regularFun()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix