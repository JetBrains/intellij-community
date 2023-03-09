// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtClass
// GROUPING_RULES: org.jetbrains.kotlin.idea.base.searching.usages.KotlinDeclarationGroupingRule
// OPTIONS: usages, constructorUsages
package server

open class <caret>Server {
    companion object {
        val NAME = "Server"
    }

    open fun work() {
        println("Server")
    }
}

// FIR_COMPARISON
