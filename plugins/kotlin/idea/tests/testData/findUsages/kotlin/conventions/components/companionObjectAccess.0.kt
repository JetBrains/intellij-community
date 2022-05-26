// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages

data class A(val <caret>x: Int, val y: Int) {
    companion object {
        fun b(): B = B(1, 2)
    }
}

data class B(val x: Int, val y: Int)

fun foo() {
    val (x, y) = A.b()
}

// FIR_COMPARISON
// FIR_COMPARISON_WITH_DISABLED_COMPONENTS