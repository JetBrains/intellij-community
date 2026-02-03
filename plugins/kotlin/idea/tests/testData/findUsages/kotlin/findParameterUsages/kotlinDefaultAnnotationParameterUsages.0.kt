// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "value: String"
annotation class KAnn(val <caret>value : String)

@KAnn("abc")
fun test1() {
}

@KAnn(value = "abc")
fun test2() {
}

// IGNORE_PLATFORM_JS: Java-specific code
// IGNORE_PLATFORM_NATIVE: Java-specific code

