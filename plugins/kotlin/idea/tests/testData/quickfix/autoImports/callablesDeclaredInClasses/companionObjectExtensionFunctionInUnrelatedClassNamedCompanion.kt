// "Import extension function 'Foo.baz'" "true"
package p

open class A {
    companion object Foo {
        fun foo() {}
    }
}

open class B {
    fun A.Foo.baz() {}
}

object BObject : B()

fun usage() {
    A.<caret>baz()
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ImportFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix