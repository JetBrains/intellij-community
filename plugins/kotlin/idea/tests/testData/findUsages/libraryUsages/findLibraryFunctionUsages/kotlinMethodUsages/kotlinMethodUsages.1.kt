// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun processRequest()"
package client

import server.Server
public class ServerEx() : Server() {
    override fun processRequest() = "foofoo"
}


