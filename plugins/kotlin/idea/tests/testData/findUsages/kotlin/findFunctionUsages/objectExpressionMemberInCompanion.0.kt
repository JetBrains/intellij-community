// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun f(): Unit"


class Foo {
    companion object {
        private val localObject = object : Any() {
            fun <caret>f() {
            }
        }
    }

    fun bar() {
        localObject.f()
    }
}