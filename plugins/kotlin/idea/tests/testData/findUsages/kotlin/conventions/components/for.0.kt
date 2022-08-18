// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages

data class A(val <caret>n: Int, val s: String, val o: Any)

fun test() {
    for ((x, y, z) in arrayOf<A>()) {
    }

    for (a in listOf<A>()) {
        val (x, y) = a
    }
}

// FIR_COMPARISON
// FIR_COMPARISON_WITH_DISABLED_COMPONENTS
// IGNORE_FIR_LOG
