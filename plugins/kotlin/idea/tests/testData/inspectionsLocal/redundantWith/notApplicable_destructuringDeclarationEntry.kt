// PROBLEM: none
// WITH_STDLIB
class A

object B {
    operator fun A.component1() : String = "1"
    operator fun A.component2() : String = "2"
}

fun dd() {
    <caret>with(B) {
        val (a, b) = A()
        println(a + b)
    }
}