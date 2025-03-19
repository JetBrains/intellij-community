// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "operator fun invoke(): Int"
package usages

import library.Foo
fun f2() {
    Foo(<caret>)
}
