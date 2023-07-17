// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun processRequest(): String"
package client

import server.processRequest

class Client {
    val methodRef = (::processRequest)()

    fun doProcessRequest() {
        println("Process...")
        processRequest<caret>()
    }
}
