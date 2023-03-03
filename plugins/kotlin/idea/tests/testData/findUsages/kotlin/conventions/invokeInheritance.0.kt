// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun invoke(): Unit"
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
