// PROBLEM: none
// WITH_STDLIB
class A

object B {
    operator fun A.iterator(): Iterator<A> = TODO()
}

fun main() {
    <caret>with(B) {
        for (a in A()) {

        }
    }
}