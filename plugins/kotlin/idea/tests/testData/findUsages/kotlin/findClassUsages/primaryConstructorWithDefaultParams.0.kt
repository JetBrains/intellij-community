// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages, constructorUsages
// PSI_ELEMENT_AS_TITLE: "class A"
class <caret>A(a: Int = 1)

fun test() {
    A(0)
    A()
}


