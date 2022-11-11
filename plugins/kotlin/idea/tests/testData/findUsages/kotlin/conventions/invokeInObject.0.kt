// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
package pack

object SomeObject {
    operator fun in<caret>voke(someString: String, someInt: Int) = println("$someString, $someInt")
}

val someObject = SomeObject("some-string1", 1)
val someObject2 = SomeObject.invoke("some-string2", 2)
val someObject3 = SomeObject
val someObject4 = someObject3("some-string1", 3)
val someObject5 = someObject3.invoke("some-string1", 4)
// FIR_COMPARISON
