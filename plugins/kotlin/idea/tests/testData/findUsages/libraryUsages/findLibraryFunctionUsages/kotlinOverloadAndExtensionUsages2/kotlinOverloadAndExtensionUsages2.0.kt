// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: overloadUsages
// PSI_ELEMENT_AS_TITLE: "fun foo(): Unit"
package usages

import library.A
import library.X
import library.O
import library.foo
class B {
    fun bar1(a: A) {
        a.foo("")
    }

    fun bar2(x: X) {
        x.foo(0)
    }

    fun bar3() {
        O.foo<caret>()
    }
}