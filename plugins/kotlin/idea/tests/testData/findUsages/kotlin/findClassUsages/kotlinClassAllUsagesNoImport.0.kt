// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages, constructorUsages, skipImports
// PSI_ELEMENT_AS_TITLE: "class Server"

package server

open class <caret>Server {
    open fun work() {
        println("Server")
    }
}

