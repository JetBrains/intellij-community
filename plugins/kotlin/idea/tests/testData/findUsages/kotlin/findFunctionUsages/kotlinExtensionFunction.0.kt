// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: overloadUsages
// PSI_ELEMENT_AS_TITLE: "fun E.myFoo(String): Unit"

class E

fun E.myFoo<caret>(s: String) {}
fun E.myFoo(d: Double) {}

fun usages(e: E) {
    e.myFoo("")
    e.myFoo(2.13)
}
