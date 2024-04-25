// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun foo(): Unit"


// IGNORE_PLATFORM_JS: Java-specific code
// IGNORE_PLATFORM_NATIVE: Java-specific code

package a

public open class Outer() {
    open class Inner {
        fun <caret>foo() {

        }
    }
}

