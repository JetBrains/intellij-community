// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtObjectDeclaration
// OPTIONS: usages

package foo

interface Iface {
    fun f()

    companion object <caret>Obj : Iface {
        override fun f() {
            companionFunction()
        }

        private fun companionFunction() {}
    }
}

fun main() {
    println(Iface.Obj)
}
