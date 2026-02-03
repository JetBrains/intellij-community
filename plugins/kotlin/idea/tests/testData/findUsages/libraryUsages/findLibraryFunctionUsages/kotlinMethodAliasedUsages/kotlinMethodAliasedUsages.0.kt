// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtImportAlias
// OPTIONS: usages
package client

import server.processRequest as process<caret>
import server.processRequest

class Client {
    fun foo() {
        process()
    }

    fun foo2() {
        processRequest()
    }
}

