// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages, constructorUsages
// PSI_ELEMENT_AS_TITLE: "class A"
package a

public open class Outer {
    public open inner class <caret>A {
        public var bar: String = "bar";

        public open fun foo() {

        }
    }
}

