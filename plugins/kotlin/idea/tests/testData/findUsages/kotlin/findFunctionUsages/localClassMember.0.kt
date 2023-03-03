// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun f(): Unit"


package anonymousUnused

fun main(args: Array<String>) {
    class LocalClass {
        fun <caret>f() {
        }
    }

    LocalClass().f()
}