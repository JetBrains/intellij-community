// "Import extension function 'Obj.foo'" "true"
package p

object Obj
open class Body {
    fun Obj.foo() {}
}

object BodyObject : Body()

fun usage() {
    Obj.<caret>foo()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix