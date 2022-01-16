// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: overrides
open class A {
    open fun <caret>foo(t: String) {
        println(t)
    }
}
