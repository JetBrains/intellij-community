// "Import extension function 'Int.ext'" "true"
package p

open class A {
    fun Int.ext() {}
}

object AObject : A()

fun usage() {
    val x: Int = 12
    x.<caret>ext()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix