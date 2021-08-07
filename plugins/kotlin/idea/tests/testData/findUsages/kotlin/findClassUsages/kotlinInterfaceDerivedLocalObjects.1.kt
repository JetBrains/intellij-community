// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: derivedClasses

fun foo() {
    val O1 = object : A() {

    }

    fun bar() {
        val O2 = object : X {

        }
    }
}
