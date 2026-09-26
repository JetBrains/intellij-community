// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "a: Int"
// COMPILER_ARGUMENTS: -Xname-based-destructuring=only-syntax
// CRI_IGNORE
data class A(val <caret>a: Int, val b: Int)

fun foo(u: A) {
    (val a1 = a, val b) = u
    println(a1)
}




