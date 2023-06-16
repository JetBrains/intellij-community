// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: textOccurrences
// PSI_ELEMENT_AS_TITLE: "fun foo(Int, String): Unit"

package test

class Foo {
    fun <caret>foo(i: Int, s: String) {

    }
}

