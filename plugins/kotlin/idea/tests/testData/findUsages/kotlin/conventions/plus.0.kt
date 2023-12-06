// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "infix operator fun plus(Int): A"

class A(val n: Int) {
    infix operator fun <caret>plus(m: Int): A = A(n + m)
    infix operator fun plus(a: A): A = this + a.n
    operator fun unaryPlus(): Unit = TODO("Not yet implemented")
}

fun test(array: Array<A>) {
    A(0) + A(1) + 2
    A(0) plus A(1) plus 2
    A(0).plus(A(1).plus(2))

    var a = A(0)
    a += 1
    a += A(1)

    +A(0)

    val (a1, a2) = array
    a1 + 1
}


// IGNORE_K2_LOG