// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import com.intellij.psi.PsiMember
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.base.analysis.api.utils.collectReceiverTypesForElement
import org.jetbrains.kotlin.idea.util.positionContext.KotlinInfixCallPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinSimpleNameReferencePositionContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtPropertyDelegate
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import org.jetbrains.kotlin.util.OperatorNameConventions


internal open class CallableImportCandidatesProvider(
    positionContext: KotlinNameReferencePositionContext,
    private val allowInapplicableExtensions: Boolean = false,
) : AbstractImportCandidatesProvider(positionContext) {

    protected open fun acceptsKotlinCallable(kotlinCallable: KtCallableDeclaration): Boolean =
        !kotlinCallable.isImported() && kotlinCallable.canBeImported()

    protected open fun acceptsJavaCallable(javaCallable: PsiMember): Boolean =
        !javaCallable.isImported() && javaCallable.canBeImported()

    protected open fun acceptsCallableCandidate(kotlinCallable: CallableImportCandidate): Boolean = true

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    override fun collectCandidates(
        indexProvider: KtSymbolFromIndexProvider,
    ): List<CallableImportCandidate> {
        val unresolvedName = positionContext.name
        val explicitReceiver = positionContext.explicitReceiver
        val fileSymbol = getFileSymbol()

        val candidates = sequence {
            if (explicitReceiver == null) {
                yieldAll(indexProvider.getKotlinCallableSymbolsByName(unresolvedName) { declaration ->
                    // filter out extensions here, because they are added later with the use of information about receiver types
                    acceptsKotlinCallable(declaration) &&  (allowInapplicableExtensions || !declaration.isExtensionDeclaration())
                }.map { CallableImportCandidate.create(it) })

                yieldAll(indexProvider.getJavaMethodsByName(unresolvedName) { acceptsJavaCallable(it) }.map { CallableImportCandidate.create(it) })
                yieldAll(indexProvider.getJavaFieldsByName(unresolvedName) { acceptsJavaCallable(it) }.map { CallableImportCandidate.create(it) })

                yieldAll(
                    indexProvider.getCallableSymbolsFromSubclassObjects(unresolvedName)
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
                        indexProvider.getExtensionCallableSymbolsByName(unresolvedName, receiverTypes) { acceptsKotlinCallable(it) }
                            .map { CallableImportCandidate.create(it) }
                    )
                    
                    yieldAll(
                        indexProvider.getExtensionCallableSymbolsFromSubclassObjects(unresolvedName, receiverTypes)
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


internal class InfixCallableImportCandidatesProvider(
    positionContext: KotlinInfixCallPositionContext,
) : CallableImportCandidatesProvider(positionContext) {

    override fun acceptsKotlinCallable(kotlinCallable: KtCallableDeclaration): Boolean =
        kotlinCallable.hasModifier(KtTokens.INFIX_KEYWORD) && super.acceptsKotlinCallable(kotlinCallable)

    override fun acceptsJavaCallable(javaCallable: PsiMember): Boolean = false

    override fun acceptsCallableCandidate(kotlinCallable: CallableImportCandidate): Boolean {
        return (kotlinCallable.symbol as? KaNamedFunctionSymbol)?.isInfix == true
    }
}


internal class DelegateMethodImportCandidatesProvider(
    private val expectedDelegateFunctionSignature: String,
    positionContext: KotlinNameReferencePositionContext,
) : CallableImportCandidatesProvider(positionContext) {

    private val expectedDelegateFunctionName: Name? = listOf(
        OperatorNameConventions.GET_VALUE,
        OperatorNameConventions.SET_VALUE,
    ).singleOrNull { expectedDelegateFunctionSignature.startsWith(it.asString() + "(") }
    
    private val missingDelegateFunctionNames: List<Name> =
        listOfNotNull(
            expectedDelegateFunctionName,
            OperatorNameConventions.PROVIDE_DELEGATE,
        )
    
    override fun acceptsKotlinCallable(kotlinCallable: KtCallableDeclaration): Boolean {
        return kotlinCallable.hasModifier(KtTokens.OPERATOR_KEYWORD) && super.acceptsKotlinCallable(kotlinCallable)
    }

    context(KaSession)
    override fun collectCandidates(
        indexProvider: KtSymbolFromIndexProvider,
    ): List<CallableImportCandidate> {
        val expressionType = positionContext.position.parentOfType<KtPropertyDelegate>()?.expression?.expressionType ?: return emptyList()
        return indexProvider.getExtensionCallableSymbolsByNameFilter(
            nameFilter = { it in missingDelegateFunctionNames },
            receiverTypes = listOf(expressionType),
        ) { acceptsKotlinCallable(it) }
            .map { CallableImportCandidate.create(it) }
            .filter { acceptsCallableCandidate(it) }
            .toList()
    }
}