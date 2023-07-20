// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun foo(Int = ...): Unit"
class Foo {
    companion object {
        @JvmStatic
        @JvmOverloads
        fun <caret>foo(n: Int = 1) {

        }
    }
}

