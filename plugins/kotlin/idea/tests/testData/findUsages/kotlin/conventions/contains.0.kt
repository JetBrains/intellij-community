// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "infix operator fun contains(Int): Boolean"

class A(val n: Int) {
    infix operator fun <caret>contains(k: Int): Boolean = k <= n
}

fun test() {
    A(2) contains 1
    1 in A(2)
    1 !in A(2)
    when (1) {
        in A(2) -> Unit
        !in A(2) -> Unit
    }
}


// IGNORE_K2_LOG