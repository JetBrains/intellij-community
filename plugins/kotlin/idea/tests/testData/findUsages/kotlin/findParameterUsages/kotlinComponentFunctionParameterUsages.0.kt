// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "foo: Int"
package test

public data class KotlinDataClass(val <caret>foo: Int, val bar: String)


// IGNORE_K2_LOG