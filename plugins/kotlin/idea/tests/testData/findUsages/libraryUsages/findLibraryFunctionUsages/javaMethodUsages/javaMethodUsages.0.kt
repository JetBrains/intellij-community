// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun processRequest()"
package usages

import testing.Server

public class ServerEx() : Server() {
    public override fun processRequest<caret>() = "foofoo"
}

