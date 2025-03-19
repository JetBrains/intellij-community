// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// CHECK_SUPER_METHODS_YES_NO_DIALOG: no
// OPTIONS: usages, skipImports
// PSI_ELEMENT_AS_TITLE: "fun foo(): Unit"
// HIGHLIGHTING
package usages

import library.I
open class A : I {
    override fun foo() {}
}

class B : A() {
    override fun <caret>foo() {}
}

fun test(i: I) {
    i.foo()
    A().foo()
    B().foo()
}

