// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages, constructorUsages
// PSI_ELEMENT_AS_TITLE: "class A"

// IGNORE_PLATFORM_JS: Java-specific code
// IGNORE_PLATFORM_NATIVE: Java-specific code

package a

public open class Outer {
    public open class Inner {
        public open class <caret>A() {
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
}

