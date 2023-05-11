// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "x: Int"

data class A(val <caret>x: Int, val y: Int)

fun x(o: Any) {
    if (o is A) {
        val (x, y) = o
        val (x1, y1) = A(1, 2)
    }
}

fun y(o: Any) {
    val list = o as List<A>
    val (x, y) = list[0]
}


// IGNORE_FIR_LOG
