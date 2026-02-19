// PROBLEM: none

class A(val x: Int) {
    suspend operator fun plus(a: A): A = A(1)
}

<caret>suspend fun foo(a1: A, a2: A): A {
    return a1 + a2
}