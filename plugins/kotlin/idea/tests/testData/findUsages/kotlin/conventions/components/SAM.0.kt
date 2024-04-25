// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "a: Int"

package pack

data class A(val <caret>a: Int, val b: String)



// IGNORE_K2_LOG
// IGNORE_PLATFORM_JS: Java-specific code
// IGNORE_PLATFORM_NATIVE: Java-specific code
