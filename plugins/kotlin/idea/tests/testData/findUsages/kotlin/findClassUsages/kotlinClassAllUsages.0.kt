// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages, constructorUsages
// PSI_ELEMENT_AS_TITLE: "class Server"
package server

open class <caret>Server {
    companion object {
        val NAME = "Server"
    }

    open fun work() {
        println("Server")
    }

    class Inner

    object InnerObject

}


