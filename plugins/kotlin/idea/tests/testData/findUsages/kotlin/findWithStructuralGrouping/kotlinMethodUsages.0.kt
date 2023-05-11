// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// GROUPING_RULES: org.jetbrains.kotlin.idea.base.searching.usages.KotlinDeclarationGroupingRule
// OPTIONS: usages
// PSI_ELEMENT_AS_TITLE: "fun processRequest()"
package server

public open class Server() {
    open fun <caret>processRequest() = "foo"
}

public class ServerEx() : Server() {
    override fun processRequest() = "foofoo"
}

