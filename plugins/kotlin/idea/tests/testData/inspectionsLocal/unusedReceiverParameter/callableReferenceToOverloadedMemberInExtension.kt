// PROBLEM: none

class C {
    fun f() {}
    fun f(x: Int) {}
}

fun <caret>C.getF(): () -> Unit = ::f

fun main() {
    C().getF()()
}