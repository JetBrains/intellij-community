// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "operator fun invoke(): Foo"
package usages

import library.Foo
fun f1() {
    Foo.invoke<caret>()
}
