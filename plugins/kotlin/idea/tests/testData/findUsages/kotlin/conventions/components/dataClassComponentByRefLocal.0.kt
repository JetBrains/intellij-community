// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages
// FIND_BY_REF

fun test() {
    data class A(val n: Int, val s: String, val o: Any)
    val a = A(1, "2", Any())
    a.n
    a.<caret>component1()
    val (x, y, z) = a
}
// FIR_COMPARISON
// FIR_COMPARISON_WITH_DISABLED_COMPONENTS
// IGNORE_FIR_LOG
