class C {
    fun f() {}
}

fun <caret>C.getF(): (C) -> Unit = C::f

fun main() {
    C().getF()(C())
}