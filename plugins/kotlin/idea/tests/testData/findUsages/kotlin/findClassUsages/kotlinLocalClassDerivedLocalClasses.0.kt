// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: derivedClasses
// PSI_ELEMENT_AS_TITLE: "class A"
fun foo() {
    open class <caret>A

    class B : A()

    open class T : A()

    fun bar() {
        class C : A()

        class D : T()
    }
}

