// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: overloadUsages
// PSI_ELEMENT_AS_TITLE: "fun c(): Unit"

class Foo {
    val c = 42
    fun <caret>c() {}
    fun m() {
        c()
        val a = c
    }
}