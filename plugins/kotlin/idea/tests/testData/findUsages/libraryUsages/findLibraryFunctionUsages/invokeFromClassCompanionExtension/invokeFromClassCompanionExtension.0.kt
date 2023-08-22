// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "operator fun Companion.invoke(): Int"
package usages
import library.Foo
import library.invoke

fun f1() {
    Foo(<caret>)
}
