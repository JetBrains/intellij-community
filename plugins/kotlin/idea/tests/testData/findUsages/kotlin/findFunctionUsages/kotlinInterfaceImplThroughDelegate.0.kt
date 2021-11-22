// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages, skipImports
package server

interface InterfaceWithImpl {
    fun <caret>foo() = 1
}

public class InterfaceWithDelegatedWithImpl(f: InterfaceWithImpl) : InterfaceWithImpl by f

fun test(twdwi: InterfaceWithDelegatedWithImpl) = twdwi.foo()

// FIR_COMPARISON