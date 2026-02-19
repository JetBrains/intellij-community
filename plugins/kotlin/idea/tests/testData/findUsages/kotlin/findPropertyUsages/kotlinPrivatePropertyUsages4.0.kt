// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtParameter
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "foo: String = ..."
package server

public open class Server(private val <caret>foo: String = "foo") {
    open fun processRequest() = foo
}

public class ServerEx() : Server(foo = "!") {
    private val foo = "f"
    override fun processRequest() = "foo" + foo
}
