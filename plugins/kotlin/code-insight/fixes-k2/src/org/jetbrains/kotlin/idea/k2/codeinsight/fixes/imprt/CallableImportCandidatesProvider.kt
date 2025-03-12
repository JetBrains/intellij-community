// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import com.intellij.psi.PsiMember
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.base.analysis.api.utils.collectReceiverTypesForElement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration

internal class CallableImportCandidatesProvider(
    override val importPositionContext: ImportPositionContext<*, *>,
    private val allowInapplicableExtensions: Boolean = false,
) : AbstractImportCandidatesProvider() {

    private fun acceptsKotlinCallable(kotlinCallable: KtCallableDeclaration): Boolean =
        acceptsKotlinCallableAtPosition(kotlinCallable) && !kotlinCallable.isImported() && kotlinCallable.canBeImported()

    private fun acceptsKotlinCallableAtPosition(kotlinCallable: KtCallableDeclaration): Boolean =
        when (importPositionContext) {
            is ImportPositionContext.InfixCall -> {
                kotlinCallable.hasModifier(KtTokens.INFIX_KEYWORD) && kotlinCallable.isExtensionDeclaration()
            }

            is ImportPositionContext.OperatorCall,
            is ImportPositionContext.Delegate,
            is ImportPositionContext.Destructuring -> {
                kotlinCallable.hasModifier(KtTokens.OPERATOR_KEYWORD) && kotlinCallable.isExtensionDeclaration()
            }

            else -> true
        }

    private fun acceptsJavaCallable(javaCallable: PsiMember): Boolean =
        acceptsJavaCallableAtPosition() && !javaCallable.isImported() && javaCallable.canBeImported()

    private fun acceptsJavaCallableAtPosition(): Boolean =
        when (importPositionContext) {
            is ImportPositionContext.InfixCall,
            is ImportPositionContext.OperatorCall,
            is ImportPositionContext.Delegate,
            is ImportPositionContext.Destructuring -> false
            else -> true
        }

    private fun acceptsCallableCandidate(kotlinCallable: CallableImportCandidate): Boolean =
        when (importPositionContext) {
            is ImportPositionContext.InfixCall -> {
                val functionSymbol = kotlinCallable.symbol as? KaNamedFunctionSymbol
                functionSymbol?.isInfix == true && functionSymbol.isExtension
            }

            is ImportPositionContext.OperatorCall,
            is ImportPositionContext.Delegate,
            is ImportPositionContext.Destructuring -> {
                val functionSymbol = kotlinCallable.symbol as? KaNamedFunctionSymbol
                functionSymbol?.isOperator == true && functionSymbol.isExtension
            }

            else -> true
        }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    override fun collectCandidates(
        name: Name,
        indexProvider: KtSymbolFromIndexProvider,
    ): List<CallableImportCandidate> {
        val explicitReceiver = importPositionContext.receiver
        val fileSymbol = getFileSymbol()

        val candidates = sequence {
            if (explicitReceiver == null) {
                yieldAll(indexProvider.getKotlinCallableSymbolsByName(name) { declaration ->
                    // filter out extensions here, because they are added later with the use of information about receiver types
                    acceptsKotlinCallable(declaration) &&  (allowInapplicableExtensions || !declaration.isExtensionDeclaration())
                }.map { CallableImportCandidate.create(it) })

                yieldAll(indexProvider.getJavaMethodsByName(name) { acceptsJavaCallable(it) }.map { CallableImportCandidate.create(it) })
                yieldAll(indexProvider.getJavaFieldsByName(name) { acceptsJavaCallable(it) }.map { CallableImportCandidate.create(it) })

                yieldAll(
                    indexProvider.getCallableSymbolsFromSubclassObjects(name)
                        .map { (dispatcherObject, callableSymbol) -> CallableImportCandidate.create(callableSymbol, dispatcherObject) }
                        .filter { allowInapplicableExtensions || !it.symbol.isExtension }
                )
            }

            when {
                allowInapplicableExtensions -> {
                    // extensions were already provided
                }
                importPositionContext is ImportPositionContext.KDocNameReference -> {
                    // we do not try to complete extensions for KDocs for now
                    // TODO consider combining this with allowInapplicableExtensions flag
                }

                else -> {
                    val receiverTypes = collectReceiverTypesForElement(importPositionContext.position, explicitReceiver as? KtExpression)
                    yieldAll(
                        indexProvider.getExtensionCallableSymbolsByName(name, receiverTypes) { acceptsKotlinCallable(it) }
                            .map { CallableImportCandidate.create(it) }
                    )

                    yieldAll(
                        indexProvider.getExtensionCallableSymbolsFromSubclassObjects(name, receiverTypes)
                            .map { (dispatcherObject, callableSymbol) -> CallableImportCandidate.create(callableSymbol, dispatcherObject) }
                    )
                }
            }
        }

        val visibilityChecker = createUseSiteVisibilityChecker(fileSymbol, receiverExpression = null, importPositionContext.position)

        return candidates
            .distinct()
            .filter { acceptsCallableCandidate(it) }
            .filter { it.isVisible(visibilityChecker) && it.callableId != null }
            .toList()
    }
}
