// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtPrimaryConstructor
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "constructor A(Int)"
// FIND_BY_REF
data class A(val n: Int)

fun test() {
    val a = A<caret>(1)
    val b = a.copy(n = 42)
}

// IGNORE_PLATFORM_JS: Java-specific code
// IGNORE_PLATFORM_NATIVE: Java-specific code