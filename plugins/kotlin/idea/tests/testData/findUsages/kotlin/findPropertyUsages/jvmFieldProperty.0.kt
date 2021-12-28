// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// OPTIONS: usages
package server

class A {
    companion object {
        @JvmField
        var <caret>foo: String = "foo"
    }
}

// FIR_COMPARISON