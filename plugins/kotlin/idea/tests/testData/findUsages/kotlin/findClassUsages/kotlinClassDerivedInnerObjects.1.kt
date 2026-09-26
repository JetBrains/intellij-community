// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: derivedClasses
// PSI_ELEMENT_AS_TITLE: ""
class Outer {
    open class Z : A() {

    }

    object O1 : A()

    class Inner {
        object O2 : Z()
    }
}
