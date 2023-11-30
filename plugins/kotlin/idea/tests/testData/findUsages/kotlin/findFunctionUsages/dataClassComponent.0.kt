// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages

public data class KotlinDataClass(val foo: Int, val bar: String)

fun test(k: KotlinDataClass) {
    k.component1<caret>()
}
