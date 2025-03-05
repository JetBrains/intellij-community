// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtProperty
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "val foo"
package server

public open class Server() {
    private val <caret>foo = "foo"

    open fun processRequest() = foo
}

public class ServerEx() : Server() {
    override fun processRequest() = "foo" + foo
}

// DISABLE_ERRORS

// ISSUE: KTIJ-29712