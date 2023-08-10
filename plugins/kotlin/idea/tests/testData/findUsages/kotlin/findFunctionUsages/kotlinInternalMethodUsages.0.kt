// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun processRequest()"
package server

public open class Server() {
    open internal fun <caret>processRequest() = "foo"
}

public class ServerEx() : Server() {
    override fun processRequest() = "foofoo"
}


