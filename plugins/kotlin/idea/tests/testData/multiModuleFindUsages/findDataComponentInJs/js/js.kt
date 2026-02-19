// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages

data class D(val <caret>a: String)

fun m(d: D) {
    d.component1()
    d.a
}

