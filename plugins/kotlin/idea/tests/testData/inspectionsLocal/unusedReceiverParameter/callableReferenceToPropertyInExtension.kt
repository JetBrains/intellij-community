// PROBLEM: none

class C {
    val p: Int = 1
}

fun <caret>C.getP() = ::p

fun main() {
    C().getP()
}