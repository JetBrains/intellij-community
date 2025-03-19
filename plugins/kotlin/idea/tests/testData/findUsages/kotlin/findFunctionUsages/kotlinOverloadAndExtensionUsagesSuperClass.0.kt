// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: overloadUsages
// PSI_ELEMENT_AS_TITLE: "fun foo(): Unit"

open class A {
    fun foo(s: String) {}
    fun A.foo() {}
}

class C: A() {
    fun fo<caret>o() {}
    
    fun m(a: A) {
        foo()
        a.foo()
        foo("")
    }
}
