// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "operator fun invoke(): Int"
package usages

import library.ClassWithInvoke
fun f1() {
    SimpleInterface.invoke<caret>()
}

interface SimpleInterface {
    companion object : ClassWithInvoke()
}
