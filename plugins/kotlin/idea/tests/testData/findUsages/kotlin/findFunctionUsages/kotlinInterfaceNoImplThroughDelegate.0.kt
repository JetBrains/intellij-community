// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages, skipImports
package server

interface InterfaceNoImpl {
    fun <caret>foo()
}

public class InterfaceWithDelegatedNoImpl(f: InterfaceNoImpl) : InterfaceNoImpl by f

fun test(twdni: InterfaceWithDelegatedNoImpl) = twdni.foo()

// FIR_COMPARISON