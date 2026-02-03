// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "val foo: Int"
package server

fun foo() {

}

val <caret>foo: Int = 1

val Int.foo: Int get() = 2


