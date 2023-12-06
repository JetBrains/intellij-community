// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: overloadUsages
// PSI_ELEMENT_AS_TITLE: "fun E.foo(String): Unit"
class E

fun E.fo<caret>o(s: String) {}
fun E.foo(d: Double) {}

fun usages(e: E) {
    e.foo("")
    e.foo(2.13)
}