// PROBLEM: none
// WITH_STDLIB
class A

object B {
    operator fun A.hasNext(): Boolean = true
    operator fun A.next() = A()
}

operator fun A.iterator() = A()

fun main() {
    <caret>with(B) {
        for (a in A()) {
        }
    }
}