// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "operator fun get(Int)"

object B {
    operator fun <caret>get(i: Int) = ""
    operator fun set(i: Int, s: String) = Unit
}

fun test() {
    B.get(2)
    B[3]
    val b = B
    b[4] = "a"
}
