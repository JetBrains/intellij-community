// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "a: Int"

package pack

data class A(val <caret>a: Int, val b: String)



// IGNORE_K2_LOG
