// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages, skipImports
// PSI_ELEMENT_AS_TITLE: "fun foo(): Int"
package client
import server.InterfaceWithImpl

public class InterfaceWithDelegatedWithImpl(f: InterfaceWithImpl) : InterfaceWithImpl by f

fun test(twdwi: InterfaceWithDelegatedWithImpl) = twdwi.foo<caret>()
