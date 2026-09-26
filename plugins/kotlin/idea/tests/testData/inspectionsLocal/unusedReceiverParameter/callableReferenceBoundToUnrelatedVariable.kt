class Other {
    fun f() {}
}

class C

fun <caret>C.getF(other: Other) = other::f

fun main() {
    C().getF(Other())()
}