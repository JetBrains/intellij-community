// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "operator fun X<Int>.component1(): Int"

class X<T>

operator fun X<Int>.<caret>component1(): Int = 0
operator fun X<Int>.component2(): Int = 0

@JvmName("comp1")
operator fun X<String>.component1(): Int = 0

@JvmName("comp2")
operator fun X<String>.component2(): Int = 0

fun f() = X<Int>()
fun g() = X<String>()

fun test() {
    val (x1, y1) = f()
    val (x2, y2) = g()
}


// IGNORE_K2_LOG
