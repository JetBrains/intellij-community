// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun f(): Unit"


package anonymousUnused

fun main(args: Array<String>) {
    fun localObject() = object : Any() {
        fun <caret>f() {
        }
    }

    localObject().f()
}