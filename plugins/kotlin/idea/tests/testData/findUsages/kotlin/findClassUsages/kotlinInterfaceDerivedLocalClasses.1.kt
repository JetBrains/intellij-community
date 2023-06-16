// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: derivedClasses
// PSI_ELEMENT_AS_TITLE: ""

fun foo() {
    open class B : A() {

    }

    open class C : Y {

    }

    fun bar() {
        open class Z : A() {

        }

        class U : Z() {

        }
    }
}
