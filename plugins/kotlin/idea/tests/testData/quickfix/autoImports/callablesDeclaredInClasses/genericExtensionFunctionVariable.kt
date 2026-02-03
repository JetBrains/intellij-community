// "Import extension function 'ext'" "true"
package p

open class A {
    fun <T> T.ext() {}
}

object AObject : A()

fun usage() {
    val ten = 10
    ten.<caret>ext()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix