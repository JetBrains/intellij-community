// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: overloadUsages
// PSI_ELEMENT_AS_TITLE: "fun E.myFoo(String): Unit"


class D
class E

fun E.my<caret>Foo(s: String) {}
fun E.myFoo(d: Double) {}
fun D.myFoo() {}

fun usages(e: E, d: D) {
    e.myFoo("")
    e.myFoo(2.13)
    d.myFoo()
}

// IGNORE_K1