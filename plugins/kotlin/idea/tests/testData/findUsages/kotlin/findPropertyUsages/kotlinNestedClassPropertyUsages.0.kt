// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "var foo: Int"
package a

public open class Outer() {
    open class Inner {
        var <caret>foo: Int = 1
    }
}

