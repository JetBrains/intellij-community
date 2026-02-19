// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun processRequest()"

// IGNORE_PLATFORM_JS: Java-specific code
// IGNORE_PLATFORM_NATIVE: Java-specific code

package testing

public open class Server() {
    public open fun <caret>processRequest() = "foo"
}

public class ServerEx() : Server() {
    public override fun processRequest() = "foofoo"
}

