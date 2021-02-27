// FIR_COMPARISON
// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: derivedClasses
fun foo() {
    open class <caret>A

    val B = object : A() {}

    open class T : A()

    fun bar() {
        val C = object : A() {}

        val D = object : T() {}
    }
}
