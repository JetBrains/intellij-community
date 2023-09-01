// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix.importFix

import com.intellij.psi.PsiMember
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.base.analysis.api.utils.collectReceiverTypesForElement
import org.jetbrains.kotlin.idea.util.positionContext.KDocNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinInfixCallPositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinNameReferencePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinSimpleNameReferencePositionContext
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration


internal open class CallableImportCandidatesProvider(
    override val positionContext: KotlinNameReferencePositionContext,
    indexProvider: KtSymbolFromIndexProvider,
) : ImportCandidatesProvider(indexProvider) {
    protected open fun acceptsKotlinCallable(kotlinCallable: KtCallableDeclaration): Boolean = kotlinCallable.canBeImported()
    protected open fun acceptsJavaCallable(javaCallable: PsiMember): Boolean = javaCallable.canBeImported()

    context(KtAnalysisSession)
    override fun collectCandidates(): List<KtCallableSymbol> {
        val unresolvedName = positionContext.getName()
        val explicitReceiver = positionContext.explicitReceiver
        val fileSymbol = getFileSymbol()

        val candidates = buildList {
            if (explicitReceiver == null) {
                addAll(indexProvider.getKotlinCallableSymbolsByName(unresolvedName) { declaration ->
                    // filter out extensions here, because they are added later with the use of information about receiver types
                    acceptsKotlinCallable(declaration) && !declaration.isExtensionDeclaration()
                })
                addAll(indexProvider.getJavaCallableSymbolsByName(unresolvedName, ::acceptsJavaCallable))
            }

            val receiverTypes = when (val context = positionContext) {
                is KotlinSimpleNameReferencePositionContext ->
                    collectReceiverTypesForElement(context.nameExpression, context.explicitReceiver)

                is KDocNameReferencePositionContext -> emptyList()
            }

            addAll(indexProvider.getTopLevelExtensionCallableSymbolsByName(unresolvedName, receiverTypes, ::acceptsKotlinCallable))
        }

        return candidates.filter { it.isVisible(fileSymbol) && it.callableIdIfNonLocal != null }
    }
}


internal class InfixCallableImportCandidatesProvider(
    override val positionContext: KotlinInfixCallPositionContext,
    indexProvider: KtSymbolFromIndexProvider,
) : CallableImportCandidatesProvider(positionContext, indexProvider) {
    override fun acceptsKotlinCallable(kotlinCallable: KtCallableDeclaration): Boolean =
        kotlinCallable.hasModifier(KtTokens.INFIX_KEYWORD) && super.acceptsKotlinCallable(kotlinCallable)

    override fun acceptsJavaCallable(javaCallable: PsiMember): Boolean = false
}