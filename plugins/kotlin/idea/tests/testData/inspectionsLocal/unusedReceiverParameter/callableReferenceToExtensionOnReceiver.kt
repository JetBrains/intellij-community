// PROBLEM: none

class C

fun C.f() {}

fun <caret>C.getF() = ::f

fun main() {
    C().getF()()
}