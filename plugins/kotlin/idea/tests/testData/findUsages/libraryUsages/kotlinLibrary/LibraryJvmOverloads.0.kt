// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun fooWithOverloads(Int, Double, String): Unit"
// FIND_BY_REF

package usages

import library.fooWithOverloads

class K {
    fun foo() {
        fooWithOverl<caret>oads()
        fooWithOverloads(1)
        fooWithOverloads(1, 1.0)
        fooWithOverloads(1, 1.0, "0")
    }
}