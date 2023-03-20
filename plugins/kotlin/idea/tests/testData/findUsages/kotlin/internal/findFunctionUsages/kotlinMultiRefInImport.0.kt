// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun foo(): Unit"
package server

internal fun <caret>foo() {

}

fun Int.foo() {

}

@JvmName("fooInt")
fun foo(n: Int) {

}

val foo: Int = 42


