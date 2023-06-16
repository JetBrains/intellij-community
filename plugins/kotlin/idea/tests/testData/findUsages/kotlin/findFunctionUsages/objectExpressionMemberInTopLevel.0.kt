// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun f(): Unit"


private val localObject = object : Any() {
    fun <caret>f() {
    }
}

class Foo {
    fun bar() {
        localObject.f()
    }
}