// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun processRequest(): String"
package client
import server.*;

class Client {
    public fun foo() {
        Server().<caret>processRequest()
        ServerEx().processRequest()
    }
}
