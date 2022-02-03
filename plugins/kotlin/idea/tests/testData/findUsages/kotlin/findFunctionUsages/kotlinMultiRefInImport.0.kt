// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
package server

fun <caret>foo() {

}

@JvmName("IntFoo")
fun Int.foo() {

}

fun foo(n: Int) {

}

val foo: Int = 42

// FIR_COMPARISON
