// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
package pack

open class Base {
    open operator fun invoke() {}
}

class Child : Base() {
    override fun <caret>invoke() {}
}

fun test() {
    val c = Child()
    c()
    c.invoke()
}
// FIR_COMPARISON