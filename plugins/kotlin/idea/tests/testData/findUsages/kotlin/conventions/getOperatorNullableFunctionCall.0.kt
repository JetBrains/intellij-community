// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "operator fun get(Int): Int"

object Y {
    operator fun <caret>get(index: Int): Int = index
    val it: Y? = this
}

fun foo() {
    val y = Y.it  // Implicit nullable type Y?
    y!!.get(1)    // Should find 'get' usage
}
