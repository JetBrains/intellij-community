class Other {
    fun f() {}
}

class C

fun <caret>C.getF(): (Other) -> Unit = Other::f

fun main() {
    C().getF()(Other())
}