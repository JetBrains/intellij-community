class C {
    companion object {
        fun f() {}
    }
}

fun <caret>C.getF() = C.Companion::f

fun main() {
    C().getF()()
}