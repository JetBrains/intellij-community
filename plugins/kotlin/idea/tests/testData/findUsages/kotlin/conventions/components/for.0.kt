// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "n: Int"

data class A(val <caret>n: Int, val s: String, val o: Any)

fun test() {
    for ((x, y, z) in arrayOf<A>()) {
    }

    for (a in listOf<A>()) {
        val (x, y) = a
    }
}


// IGNORE_FIR_LOG
