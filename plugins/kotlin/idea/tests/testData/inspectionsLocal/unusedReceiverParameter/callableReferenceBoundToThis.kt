// PROBLEM: none

class C {
    fun f() {}
}

fun <caret>C.getF() = this::f

fun main() {
    C().getF()()
}