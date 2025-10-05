// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.contributors

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.collectImplicitReceiverTypes
import org.jetbrains.kotlin.analysis.api.components.dispatchReceiverType
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.isSubtypeOf
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.completion.ItemPriority
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSectionContext
import org.jetbrains.kotlin.idea.completion.impl.k2.K2CompletionSetupScope
import org.jetbrains.kotlin.idea.completion.impl.k2.K2SimpleCompletionContributor
import org.jetbrains.kotlin.idea.completion.impl.k2.context.getOriginalDeclarationOrSelf
import org.jetbrains.kotlin.idea.completion.priority
import org.jetbrains.kotlin.idea.completion.referenceScope
import org.jetbrains.kotlin.idea.completion.suppressAutoInsertion
import org.jetbrains.kotlin.idea.util.positionContext.*
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
internal class K2DeclarationFromUnresolvedNameContributor : K2SimpleCompletionContributor<KotlinRawPositionContext>(
    KotlinRawPositionContext::class
) {
    context(_: KaSession, context: K2CompletionSectionContext<KotlinRawPositionContext>)
    override fun complete() {
        val declaration = context.positionContext.position.getCurrentDeclarationAtCaret() ?: return
        val referenceScope = referenceScope(declaration) ?: return

        referenceScope.forEachDescendantOfType<KtNameReferenceExpression>(
            canGoInside = { it !is KtPackageDirective && it !is KtImportDirective }
        ) { refExpr ->
            ProgressManager.checkCanceled()
            processReference(referenceScope, declaration, refExpr)
        }
    }

    context(_: KaSession, context: K2CompletionSectionContext<KotlinRawPositionContext>)
    private fun processReference(
        referenceScope: KtElement,
        currentDeclarationInFakeFile: KtNamedDeclaration,
        unresolvedRef: KtNameReferenceExpression
    ) {
        // In a block, references must be after the declaration. Therefore, we only offer completion for unresolved names that appear after
        // the current cursor position.
        if (referenceScope is KtBlockExpression && unresolvedRef.startOffset < context.parameters.offset) {
            return
        }
        val name = unresolvedRef.getReferencedName()
        if (!context.prefixMatcher.prefixMatches(name)) return

        val originalCurrentDeclaration = getOriginalDeclarationOrSelf(currentDeclarationInFakeFile, context.completionContext.originalFile)
        if (!shouldOfferCompletion(unresolvedRef, originalCurrentDeclaration)) return

        if (unresolvedRef.reference?.resolve() == null) {
            val lookupElement = LookupElementBuilder.create(name).suppressAutoInsertion()
                .also { it.priority = ItemPriority.FROM_UNRESOLVED_NAME_SUGGESTION }
            context.addElement(lookupElement)
        }
    }

    context(_: KaSession)
    private fun shouldOfferCompletion(unresolvedRef: KtNameReferenceExpression, currentDeclaration: KtNamedDeclaration): Boolean {
        val refExprParent = unresolvedRef.parent
        val receiver = if (refExprParent is KtCallExpression) {
            refExprParent.getReceiverForSelector()
        } else {
            unresolvedRef.getReceiverForSelector()
        }
        return when (val symbol = currentDeclaration.symbol) {
            is KaCallableSymbol -> when {
                refExprParent is KtUserType -> false

                // If current declaration is a function, we only offer names of unresolved function calls. For property declarations, we
                // always offer an unresolved usage because a property may be called if the object has an `invoke` method.
                refExprParent !is KtCallableReferenceExpression && refExprParent !is KtCallExpression && symbol is KaFunctionSymbol -> false

                receiver != null -> {
                    val actualReceiverType = receiver.expressionType ?: return false
                    val expectedReceiverType = getReceiverType(symbol) ?: return false

                    // FIXME: this check does not work with generic types (i.e. List<String> and List<T>)
                    actualReceiverType.isSubtypeOf(expectedReceiverType)
                }

                else -> {
                    // If there is no explicit receiver at call-site, we check if any implicit receiver at call-site matches the extension
                    // receiver type for the current declared symbol
                    val extensionReceiverType = symbol.receiverType ?: return true
                    collectImplicitReceiverTypes(unresolvedRef).any { it.isSubtypeOf(extensionReceiverType) }
                }
            }

            is KaClassSymbol -> when {
                receiver != null -> false
                refExprParent is KtUserType -> true
                refExprParent is KtCallExpression -> symbol.classKind == KaClassKind.CLASS
                else -> symbol.classKind == KaClassKind.OBJECT
            }

            else -> false
        }
    }

    private fun PsiElement.getReceiverForSelector(): KtExpression? {
        val qualifiedExpression = (parent as? KtDotQualifiedExpression)?.takeIf { it.selectorExpression == this } ?: return null
        return qualifiedExpression.receiverExpression
    }

    context(_: KaSession)
    private fun getReceiverType(symbol: KaCallableSymbol): KaType? {
        return symbol.receiverType ?: symbol.dispatchReceiverType
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

    override fun K2CompletionSetupScope<KotlinRawPositionContext>.isAppropriatePosition(): Boolean = when (position) {
        is KotlinTypeNameReferencePositionContext,
        is KotlinClassifierNamePositionContext,
        is KotlinSimpleParameterPositionContext,
        is KotlinPrimaryConstructorParameterPositionContext -> true

        else -> false
    }

    override fun K2CompletionSectionContext<KotlinRawPositionContext>.getGroupPriority(): Int = when (positionContext) {
        is KotlinTypeNameReferencePositionContext, is KotlinClassifierNamePositionContext -> 1
        else -> 0
    }

}