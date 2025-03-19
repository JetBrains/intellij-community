// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// OPTIONS: usages, constructorUsages
// PSI_ELEMENT_AS_TITLE: "class Foo"


package server

public open class Server() {
    private class <caret>Foo {

    }

    open fun processRequest() = Foo()
}

public class ServerEx() : Server() {
    override fun processRequest() = Server.Foo()
}

// DISABLE_ERRORS