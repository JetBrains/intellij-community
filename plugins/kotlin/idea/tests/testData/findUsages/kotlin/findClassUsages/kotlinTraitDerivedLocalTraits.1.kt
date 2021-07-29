// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: derivedInterfaces

fun foo() {
    open class B : A() {

    }

    fun bar() {
        open class Z : A() {

        }

        class U : Z() {

        }
    }
}
