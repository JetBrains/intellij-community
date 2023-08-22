// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages, skipImports
// PSI_ELEMENT_AS_TITLE: "fun foo(): Unit"
package client

import server.InterfaceNoImpl

public class InterfaceWithDelegatedNoImpl(f: InterfaceNoImpl) : InterfaceNoImpl by f

fun test(twdni: InterfaceWithDelegatedNoImpl) = twdni.foo<caret>()

