// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
package server

internal fun <caret>foo() {

}

fun Int.foo() {

}

@JvmName("fooInt")
fun foo(n: Int) {

}

val foo: Int = 42

// FIR_COMPARISON
