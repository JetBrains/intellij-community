// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun foo(): Unit"
class Foo {
    companion object {
        @JvmStatic
        fun <caret>foo() {

        }
    }
}

