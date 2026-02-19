// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: derivedClasses
// PSI_ELEMENT_AS_TITLE: "class A"
fun foo() {
    open class <caret>A

    val b = object : A() {}

    open class T : A()

    fun bar() {
        val c = object : A() {}

        val d = object : T() {}
    }
}

