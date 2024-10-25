// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import com.intellij.psi.PsiMember
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.base.analysis.api.utils.collectReceiverTypesForElement
import org.jetbrains.kotlin.idea.highlighter.KotlinUnresolvedReferenceKind.UnresolvedDelegateFunction
import org.jetbrains.kotlin.idea.util.positionContext.KotlinInfixCallPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinSimpleNameReferencePositionContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtPropertyDelegate
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import org.jetbrains.kotlin.util.OperatorNameConventions


internal open class CallableImportCandidatesProvider(
    positionContext: KotlinNameReferencePositionContext,
) : ImportCandidatesProvider(positionContext) {

    protected open fun acceptsKotlinCallable(kotlinCallable: KtCallableDeclaration): Boolean =
        !kotlinCallable.isImported() && kotlinCallable.canBeImported()

    protected open fun acceptsJavaCallable(javaCallable: PsiMember): Boolean =
        !javaCallable.isImported() && javaCallable.canBeImported()

    context(KaSession)
    override fun collectCandidates(
        indexProvider: KtSymbolFromIndexProvider,
    ): List<KaCallableSymbol> {
        val unresolvedName = positionContext.getName()
        val explicitReceiver = positionContext.explicitReceiver
        val fileSymbol = getFileSymbol()

        val candidates = buildList {
            if (explicitReceiver == null) {
                addAll(indexProvider.getKotlinCallableSymbolsByName(unresolvedName) { declaration ->
                    // filter out extensions here, because they are added later with the use of information about receiver types
                    acceptsKotlinCallable(declaration) && !declaration.isExtensionDeclaration()
                })
                addAll(indexProvider.getJavaMethodsByName(unresolvedName) { acceptsJavaCallable(it) })
                addAll(indexProvider.getJavaFieldsByName(unresolvedName) { acceptsJavaCallable(it) })
            }

            when (val context = positionContext) {
                is KotlinSimpleNameReferencePositionContext -> {
                    val receiverTypes = collectReceiverTypesForElement(context.nameExpression, context.explicitReceiver)
                    addAll(indexProvider.getExtensionCallableSymbolsByName(unresolvedName, receiverTypes) { acceptsKotlinCallable(it) })
                }

                else -> {}
            }
        }

        return candidates.filter { it.isVisible(fileSymbol) && it.callableId != null }
    }
}


internal class InfixCallableImportCandidatesProvider(
    positionContext: KotlinInfixCallPositionContext,
) : CallableImportCandidatesProvider(positionContext) {

    override fun acceptsKotlinCallable(kotlinCallable: KtCallableDeclaration): Boolean =
        kotlinCallable.hasModifier(KtTokens.INFIX_KEYWORD) && super.acceptsKotlinCallable(kotlinCallable)

    override fun acceptsJavaCallable(javaCallable: PsiMember): Boolean = false
}


internal class DelegateMethodImportCandidatesProvider(
    private val unresolvedDelegateFunction: UnresolvedDelegateFunction,
    positionContext: KotlinNameReferencePositionContext,
) : CallableImportCandidatesProvider(positionContext) {

    context(KaSession)
    override fun collectCandidates(
        indexProvider: KtSymbolFromIndexProvider,
    ): List<KaCallableSymbol> {
        val functionName = OperatorNameConventions.GET_VALUE.takeIf {
            unresolvedDelegateFunction.expectedFunctionSignature.startsWith(OperatorNameConventions.GET_VALUE.asString() + "(")
        } ?: OperatorNameConventions.SET_VALUE.takeIf {
            unresolvedDelegateFunction.expectedFunctionSignature.startsWith(OperatorNameConventions.SET_VALUE.asString() + "(")
        } ?: return emptyList()

        val expressionType = positionContext.position.parentOfType<KtPropertyDelegate>()?.expression?.expressionType ?: return emptyList()
        return indexProvider.getExtensionCallableSymbolsByName(
            name = functionName,
            receiverTypes = listOf(expressionType),
        ) { acceptsKotlinCallable(it) }
            .toList()
    }
}