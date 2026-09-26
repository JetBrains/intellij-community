fun f() {}

fun <caret>C.getF() = ::f

class C

fun main() {
    C().getF()()
}