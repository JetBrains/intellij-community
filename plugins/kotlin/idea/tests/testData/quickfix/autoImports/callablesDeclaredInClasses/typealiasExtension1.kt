// "Import extension function 'Obj.ext'" "true"
package p

object Obj
typealias StrangeName = Obj

open class Foo {
    fun Obj.ext() {}
}

object FooObj : Foo()

fun usage() {
    Obj.<caret>ext()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix