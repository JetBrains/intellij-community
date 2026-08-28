// PROBLEM: none

class C {
    fun f() {}
}

fun <caret>C.getF() = ::f

fun main() {
    C().getF()()
}