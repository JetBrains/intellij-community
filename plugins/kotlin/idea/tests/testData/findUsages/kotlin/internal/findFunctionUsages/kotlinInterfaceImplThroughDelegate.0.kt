// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages, skipImports
// PSI_ELEMENT_AS_TITLE: "fun foo()"
package server

interface InterfaceWithImpl {
    internal fun <caret>foo() = 1
}

public class InterfaceWithDelegatedWithImpl(f: InterfaceWithImpl) : InterfaceWithImpl by f

fun test(twdwi: InterfaceWithDelegatedWithImpl) = twdwi.foo()


// DISABLE_ERRORS