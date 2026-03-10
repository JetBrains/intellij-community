// "Import property 'RegObject.regularProperty'" "true"
// K2_ERROR: Unresolved reference 'regularProperty'.
package p

open class Reg {
    val regularProperty = 1
}

object RegObject : Reg()

fun usage() {
    val x = <caret>regularProperty
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix