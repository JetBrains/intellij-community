// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: derivedClasses
// PSI_ELEMENT_AS_TITLE: ""
class Outer {
    open class B : A() {

    }

    open class C : Y {

    }

    class Inner {
        open class Z : A() {

        }

        class U : Z() {

        }
    }
}
