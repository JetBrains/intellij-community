// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun foo(): Unit"
package a

public open class Outer() {
    open class Inner {
        fun <caret>foo() {

        }
    }
}

