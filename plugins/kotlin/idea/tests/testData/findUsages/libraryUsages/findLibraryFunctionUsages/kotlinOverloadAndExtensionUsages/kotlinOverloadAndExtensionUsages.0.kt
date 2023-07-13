// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: overloadUsages
// PSI_ELEMENT_AS_TITLE: "fun foo(T): Unit"
package usages

import library.A
import library.foo

open class B : A<String>() {
    override fun foo(t: String) {
        super<A>.foo<caret>(t)
    }

    open fun bas(a: A<Number>) {
        a.foo(0, "")
    }
}