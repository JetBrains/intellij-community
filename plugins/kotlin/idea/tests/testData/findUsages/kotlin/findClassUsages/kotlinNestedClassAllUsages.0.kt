// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages, constructorUsages
// PSI_ELEMENT_AS_TITLE: "class A"
package a

public open class Outer {
    public open class <caret>A {
        public var bar: String = "bar";

        public open fun foo() {

        }

        companion object {
            public var bar: String = "bar";

            public open fun foo() {

            }
        }
    }
}

