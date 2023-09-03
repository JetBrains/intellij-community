// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "operator fun P.invoke()"
package pack

class P

operator fun P.<caret>invoke() = 1

fun f(p: P) {
    p()
    p.invoke()
}


// IGNORE_K2_LOG