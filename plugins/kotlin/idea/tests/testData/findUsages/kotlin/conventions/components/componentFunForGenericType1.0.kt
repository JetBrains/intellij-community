// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "operator fun <T> X<T>.component1(): Int"

class X<T>

operator fun <T> X<T>.<caret>component1(): Int = 0
operator fun <T> X<T>.component2(): Int = 0

fun f() = X<String>()

fun test() {
    val (x, y) = f()
}



// IGNORE_K2_LOG
