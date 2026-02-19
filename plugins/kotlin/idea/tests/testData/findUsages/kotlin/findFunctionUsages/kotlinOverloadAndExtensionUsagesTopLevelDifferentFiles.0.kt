// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: overloadUsages
// PSI_ELEMENT_AS_TITLE: "fun foo(String): Unit"

package p

fun f<caret>oo(s: String) {}

fun usages() {
    foo("")
    foo(2.13)
}
