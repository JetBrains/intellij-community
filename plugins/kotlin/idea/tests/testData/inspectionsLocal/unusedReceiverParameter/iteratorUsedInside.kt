// PROBLEM: none
// WITH_STDLIB

class A

object B {
    operator fun A.iterator(): Iterator<A> = TODO()
}

fun <caret>B.main() {
    for (a in A()) {
    }
}