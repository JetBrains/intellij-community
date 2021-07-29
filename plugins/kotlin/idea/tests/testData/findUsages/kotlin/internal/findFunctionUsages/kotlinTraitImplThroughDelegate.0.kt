// PSI_ELEMENT: org.jetbrains.kotlin.psi.KtNamedFunction
// OPTIONS: usages, skipImports
package server

interface TraitWithImpl {
    @Suppress("WRONG_MODIFIER_CONTAINING_DECLARATION") internal fun <caret>foo() = 1
}

public class TraitWithDelegatedWithImpl(f: TraitWithImpl): TraitWithImpl by f

fun test(twdwi: TraitWithDelegatedWithImpl) = twdwi.foo()

// FIR_COMPARISON
// CRI_IGNORE