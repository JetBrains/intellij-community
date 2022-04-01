// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// OPTIONS: usages
package server

public open class Server() {
    private val <caret>foo = "foo"

    open fun processRequest() = foo
}

public class ServerEx() : Server() {
    private val foo = "foo"
    override fun processRequest() = "foo" + foo
}


// FIR_COMPARISON