// PROBLEM: none

open class D {
    fun f() {}
}

class C : D()

fun <caret>C.getF() = ::f

fun main() {
    C().getF()()
}