// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "operator fun unaryMinus(): A"

class A(val n: Int) {
    operator fun <caret>unaryMinus(): A = this
}

fun test() {
    A(1).unaryMinus()
    -A(1)
}


// IGNORE_K2_LOG