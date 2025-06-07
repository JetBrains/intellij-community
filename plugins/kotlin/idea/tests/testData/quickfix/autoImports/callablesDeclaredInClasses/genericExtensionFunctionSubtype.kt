// "Import extension function 'bar'" "true"
package p

open class A
open class B : A()

fun B.usage() {
    <caret>bar()
}

open class C {
    fun <T : A> T.bar() {}
}

object CObject : C()

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix