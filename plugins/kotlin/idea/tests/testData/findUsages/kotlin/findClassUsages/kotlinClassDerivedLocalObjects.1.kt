// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: derivedClasses
// PSI_ELEMENT_AS_TITLE: ""

fun foo() {
    open class Z : A() {

    }

    val O1 = object : A() {}

    fun bar() {
        val O2 = object : Z() {}
    }
}
