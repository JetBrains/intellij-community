// "Rename reference" "true"
// ERROR: Unresolved reference: x
// ERROR: Unresolved reference: x
package x

class A {
    val a = 1
    val s = ""

    fun bar(i: Int) {

    }

    fun baz(i: Int) {

    }

    fun foo() {
        bar(<caret>x)
        baz(x)
        bar(x())
        baz(x(1))
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RenameUnresolvedReferenceFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.RenameUnresolvedReferenceFix