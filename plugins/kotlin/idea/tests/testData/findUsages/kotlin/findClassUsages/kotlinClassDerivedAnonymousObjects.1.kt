// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: derivedClasses
// PSI_ELEMENT_AS_TITLE: ""

fun foo() {
    open class Z : A() {

    }

    fun doSomething(x: A, y: A) {

    }

    doSomething(object : A() {}, object : Z() {})

    fun bar() {
        val x = object : Z() {

        }
    }
}
