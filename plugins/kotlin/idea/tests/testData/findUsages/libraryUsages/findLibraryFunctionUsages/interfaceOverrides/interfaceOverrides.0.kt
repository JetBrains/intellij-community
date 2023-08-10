// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: overrides
// PSI_ELEMENT_AS_TITLE: "fun foo(String): Unit"
package usages

import library.A
open class Bawdaw : A {
    override fun foo<caret>(t: String) {

    }
}