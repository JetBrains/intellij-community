// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages

package pack

data class A(val <caret>a: Int, val b: String)

// FIR_COMPARISON
// FIR_COMPARISON_WITH_DISABLED_COMPONENTS
// IGNORE_FIR_LOG
