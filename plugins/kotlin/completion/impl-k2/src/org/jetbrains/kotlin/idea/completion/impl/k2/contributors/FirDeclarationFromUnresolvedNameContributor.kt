// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.completion.contributors

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.idea.completion.FirCompletionSessionParameters
import org.jetbrains.kotlin.idea.completion.ItemPriority
import org.jetbrains.kotlin.idea.completion.context.FirBasicCompletionContext
import org.jetbrains.kotlin.idea.completion.context.getOriginalDeclarationOrSelf
import org.jetbrains.kotlin.idea.completion.priority
import org.jetbrains.kotlin.idea.completion.referenceScope
import org.jetbrains.kotlin.idea.completion.suppressAutoInsertion
import org.jetbrains.kotlin.idea.completion.weighers.WeighingContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.forEachDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/**
 * Complete names of a completion by name of unresolved references in the code. For example
 * ```
 * val unr<caret>
 * println(unresolvedVar)
 * ```
 * This contributor would contribute `unresolvedVar` at caret position above.
 */
internal class FirDeclarationFromUnresolvedNameContributor(
    basicContext: FirBasicCompletionContext,
    priority: Int,
) : FirCompletionContributorBase<KotlinRawPositionContext>(basicContext, priority) {
    context(KtAnalysisSession)
    override fun complete(
        positionContext: KotlinRawPositionContext,
        weighingContext: WeighingContext,
        sessionParameters: FirCompletionSessionParameters,
    ) {
        val declaration = positionContext.position.getCurrentDeclarationAtCaret() ?: return
        val referenceScope = referenceScope(declaration) ?: return

        referenceScope.forEachDescendantOfType<KtNameReferenceExpression>(
            canGoInside = { it !is KtPackageDirective && it !is KtImportDirective }
        ) { refExpr ->
            ProgressManager.checkCanceled()
            processReference(referenceScope, declaration, refExpr)
        }
    }

    context(KtAnalysisSession)
    private fun processReference(
        referenceScope: KtElement,
        currentDeclarationInFakeFile: KtNamedDeclaration,
        unresolvedRef: KtNameReferenceExpression
    ) {
        // In a block, references must be after the declaration. Therefore, we only offer completion for unresolved names that appear after
        // the current cursor position.
        if (referenceScope is KtBlockExpression && unresolvedRef.startOffset < parameters.offset) {
            return
        }
        val name = unresolvedRef.getReferencedName()
        if (!prefixMatcher.prefixMatches(name)) return

        val originalCurrentDeclaration = getOriginalDeclarationOrSelf(currentDeclarationInFakeFile, basicContext.originalKtFile)
        if (!shouldOfferCompletion(unresolvedRef, originalCurrentDeclaration)) return

        if (unresolvedRef.reference?.resolve() == null) {
            val lookupElement = LookupElementBuilder.create(name).suppressAutoInsertion()
                .also { it.priority = ItemPriority.FROM_UNRESOLVED_NAME_SUGGESTION }
            sink.addElement(lookupElement)
        }
    }

    context(KtAnalysisSession)
    private fun shouldOfferCompletion(unresolvedRef: KtNameReferenceExpression, currentDeclaration: KtNamedDeclaration): Boolean {
        val refExprParent = unresolvedRef.parent
        val receiver = if (refExprParent is KtCallExpression) {
            refExprParent.getReceiverForSelector()
        } else {
            unresolvedRef.getReceiverForSelector()
        }
        return when (val symbol = currentDeclaration.getSymbol()) {
            is KtCallableSymbol -> when {
                refExprParent is KtUserType -> false

                // If current declaration is a function, we only offer names of unresolved function calls. For property declarations, we
                // always offer an unresolved usage because a property may be called if the object has an `invoke` method.
                refExprParent !is KtCallableReferenceExpression && refExprParent !is KtCallExpression && symbol is KtFunctionLikeSymbol -> false

                receiver != null -> {
                    val actualReceiverType = receiver.getKtType() ?: return false
                    val expectedReceiverType = getReceiverType(symbol) ?: return false

                    // FIXME: this check does not work with generic types (i.e. List<String> and List<T>)
                    actualReceiverType isSubTypeOf expectedReceiverType
                }
                else -> {
                    // If there is no explicit receiver at call-site, we check if any implicit receiver at call-site matches the extension
                    // receiver type for the current declared symbol
                    val extensionReceiverType = symbol.receiverType ?: return true
                    getImplicitReceiverTypesAtPosition(unresolvedRef).any { it isSubTypeOf extensionReceiverType }
                }
            }
            is KtClassOrObjectSymbol -> when {
                receiver != null -> false
                refExprParent is KtUserType -> true
                refExprParent is KtCallExpression -> symbol.classKind == KtClassKind.CLASS
                else -> symbol.classKind == KtClassKind.OBJECT
            }
            else -> false
        }
    }

    private fun PsiElement.getReceiverForSelector(): KtExpression? {
        val qualifiedExpression = (parent as? KtDotQualifiedExpression)?.takeIf { it.selectorExpression == this } ?: return null
        return qualifiedExpression.receiverExpression
    }

    context(KtAnalysisSession)
    private fun getReceiverType(symbol: KtCallableSymbol): KtType? {
        return symbol.receiverType ?: (symbol as? KtCallableSymbol)?.getDispatchReceiverType()
    }

    private fun PsiElement.getCurrentDeclarationAtCaret(): KtNamedDeclaration? {
        return when (val parent = parent) {
            is KtNamedDeclaration -> parent
            is KtNameReferenceExpression -> {
                // In a fake completion file, a property or function under construction is appended with placeholder text "X.f$", which is
                // parsed as a type reference. For example, if the original file has the following content,
                //
                // ```
                // val myProp<caret>
                // ```
                //
                // the fake file would then contain
                //
                // ```
                // val myPropX.f$
                // ```
                //
                // In this case, the Kotlin PSI parser treats `myPropX` as the receiver type reference for an extension property.
                // See org.jetbrains.kotlin.idea.completion.CompletionDummyIdentifierProviderService#specialExtensionReceiverDummyIdentifier
                // for more details.
                val userType = parent.parent as? KtUserType ?: return null
                val typeRef = userType.parent as? KtTypeReference ?: return null
                val callable = (typeRef.parent as? KtCallableDeclaration)?.takeIf { it.receiverTypeReference == typeRef } ?: return null
                callable
            }
            else -> null
        }
    }
}