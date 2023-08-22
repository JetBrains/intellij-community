// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "var foo: String"
package server

class A {
    companion object {
        var <caret>foo: String = "foo"
    }
}

