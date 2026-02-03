// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages

public data class KotlinDataClass(val foo: Int, val bar: String)

fun test(k: KotlinDataClass) {
    k.component1<caret>()
}

class V1<T> where T: KotlinDataClass {
    fun foo(t1: T) {
        val (f1, b1) = t1
    }
}

class V2<T: KotlinDataClass> {
    fun foo(t2: T) {
        val (f2, b2) = t2
    }
}

typealias TypeAliasForKotlinDataClass = KotlinDataClass

fun testAlias(kdc: TypeAliasForKotlinDataClass, l: List<TypeAliasForKotlinDataClass>) {
    val (f3, b3) = kdc
    val (f4, b4) = l.first()
}
