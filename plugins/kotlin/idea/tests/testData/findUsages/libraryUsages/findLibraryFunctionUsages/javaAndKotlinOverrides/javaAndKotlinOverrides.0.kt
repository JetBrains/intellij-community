// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: overrides
// PSI_ELEMENT_AS_TITLE: "fun foo(T): Unit"
package usages

import library.A

fun <T> A<T>.foo(t: T, x: String) {
    foo(t)
    println(x)
}

fun A<String>.foo(s: String, n: Number) {
    fun <T> A<T>.foo(t: T, x: String) {
        foo(t)
        println(x)
    }

    foo(s)
    println(n)
}

open class B : A<String>() {
    override fun foo(t: String) {
        super<A>.foo<caret>(t)
    }

    open fun baz(a: A<String>) {
        a.foo("", 0)
    }

    open fun bas(a: A<Number>) {
        a.foo(0, "")
    }
}
