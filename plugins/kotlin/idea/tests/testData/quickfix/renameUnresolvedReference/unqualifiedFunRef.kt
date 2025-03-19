// "Rename reference" "true"
// ERROR: Unresolved reference: x
// ERROR: Unresolved reference: x
// ERROR: Unresolved reference: x
// ERROR: Unresolved reference: x
// K2_AFTER_ERROR: Unresolved reference 'x'.
// K2_AFTER_ERROR: Unresolved reference 'x'.
// K2_AFTER_ERROR: Unresolved reference 'x'.
// K2_AFTER_ERROR: Unresolved reference 'x'.
class A {
    fun f() = 1
    fun g() = ""

    fun bar(i: Int) {

    }

    fun baz(i: Int) {

    }

    fun foo() {
        bar(x)
        baz(x)
        bar(<caret>x())
        baz(x())
        bar(x(1))
        baz(x(1))
    }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RenameUnresolvedReferenceFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.RenameUnresolvedReferenceFix