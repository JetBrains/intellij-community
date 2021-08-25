// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: overloadUsages
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
