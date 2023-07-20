// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: overloadUsages
// PSI_ELEMENT_AS_TITLE: "fun foo(): Unit"
interface X {

}

open class A : X {

}

object O : A() {
    fun <caret>foo() {

    }
}

fun A.foo(s: String) {

}

fun X.foo(n: Int) {

}

