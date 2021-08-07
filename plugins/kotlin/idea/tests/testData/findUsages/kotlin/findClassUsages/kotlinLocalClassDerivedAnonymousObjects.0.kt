// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: derivedClasses
fun foo() {
    open class <caret>A

    val b = object : A() {}

    open class T : A()

    fun bar() {
        val c = object : A() {}

        val d = object : T() {}
    }
}
