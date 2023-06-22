// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun foo(): Unit"
package server

fun <caret>foo() {

}

@JvmName("IntFoo")
fun Int.foo() {

}

fun foo(n: Int) {

}

val foo: Int = 42


