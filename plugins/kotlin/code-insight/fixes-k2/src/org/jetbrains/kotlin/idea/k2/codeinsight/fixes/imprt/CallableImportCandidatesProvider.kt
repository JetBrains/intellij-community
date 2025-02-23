// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import com.intellij.psi.PsiMember
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.base.analysis.api.utils.collectReceiverTypesForElement
import org.jetbrains.kotlin.idea.util.positionContext.KotlinInfixCallPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinOperatorCallPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinSimpleNameReferencePositionContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration

internal class CallableImportCandidatesProvider(
    override val positionContext: KotlinNameReferencePositionContext,
    private val allowInapplicableExtensions: Boolean = false,
) : AbstractImportCandidatesProvider() {

    private fun acceptsKotlinCallable(kotlinCallable: KtCallableDeclaration): Boolean =
        acceptsKotlinCallableAtPosition(kotlinCallable) && !kotlinCallable.isImported() && kotlinCallable.canBeImported()

    private fun acceptsKotlinCallableAtPosition(kotlinCallable: KtCallableDeclaration): Boolean =
        when (positionContext) {
            is KotlinInfixCallPositionContext -> kotlinCallable.hasModifier(KtTokens.INFIX_KEYWORD)
            is KotlinOperatorCallPositionContext -> kotlinCallable.hasModifier(KtTokens.OPERATOR_KEYWORD)
            else -> true
        }

    private fun acceptsJavaCallable(javaCallable: PsiMember): Boolean =
        acceptsJavaCallableAtPosition() && !javaCallable.isImported() && javaCallable.canBeImported()

    private fun acceptsJavaCallableAtPosition(): Boolean =
        when (positionContext) {
            is KotlinInfixCallPositionContext, 
            is KotlinOperatorCallPositionContext -> false
            else -> true
        }

    private fun acceptsCallableCandidate(kotlinCallable: CallableImportCandidate): Boolean =
        when (positionContext) {
            is KotlinInfixCallPositionContext -> (kotlinCallable.symbol as? KaNamedFunctionSymbol)?.isInfix == true
            is KotlinOperatorCallPositionContext -> (kotlinCallable.symbol as? KaNamedFunctionSymbol)?.isOperator == true
            else -> true
        }

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    override fun collectCandidates(
        name: Name,
        indexProvider: KtSymbolFromIndexProvider,
    ): List<CallableImportCandidate> {
        val explicitReceiver = positionContext.explicitReceiver
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

            val context = positionContext
            when {
                allowInapplicableExtensions -> {
                    // extensions were already provided
                }
                context is KotlinSimpleNameReferencePositionContext -> {
                    val receiverTypes = collectReceiverTypesForElement(context.nameExpression, context.explicitReceiver)
                    yieldAll(
                        indexProvider.getExtensionCallableSymbolsByName(name, receiverTypes) { acceptsKotlinCallable(it) }
                            .map { CallableImportCandidate.create(it) }
                    )
                    
                    yieldAll(
                        indexProvider.getExtensionCallableSymbolsFromSubclassObjects(name, receiverTypes)
                            .map { (dispatcherObject, callableSymbol) -> CallableImportCandidate.create(callableSymbol, dispatcherObject) }
                    )
                }

                else -> {}
            }
        }

        val visibilityChecker = createUseSiteVisibilityChecker(fileSymbol, receiverExpression = null, positionContext.position)

        return candidates
            .distinct()
            .filter { acceptsCallableCandidate(it) }
            .filter { it.isVisible(visibilityChecker) && it.callableId != null }
            .toList()
    }
}
