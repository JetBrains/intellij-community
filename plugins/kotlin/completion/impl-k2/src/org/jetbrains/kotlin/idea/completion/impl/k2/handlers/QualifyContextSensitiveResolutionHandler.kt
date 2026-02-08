// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2.handlers

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.startOffset
import kotlinx.serialization.Serializable
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.analysis.withRootPrefixIfNeeded
import org.jetbrains.kotlin.idea.base.codeInsight.ShortenReferencesFacility
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.prependDotQualifiedReceiver
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.serialization.names.KotlinFqNameSerializer
import org.jetbrains.kotlin.idea.codeinsight.utils.getLeftMostReceiverExpression
import org.jetbrains.kotlin.idea.completion.impl.k2.KotlinFirCompletionParameters
import org.jetbrains.kotlin.idea.completion.impl.k2.KotlinFirCompletionParameters.Corrected
import org.jetbrains.kotlin.idea.completion.impl.k2.KotlinFirCompletionParameters.CorrectionType
import org.jetbrains.kotlin.idea.completion.api.serialization.SerializableInsertHandler
import org.jetbrains.kotlin.idea.completion.impl.k2.contributors.withChainedInsertHandler
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.UserDataProperty
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

/**
 * Inserts the [fullyQualifiedName] at the [exprOffset] for expressions that require a fully-qualified name to be used
 * after a completion result prevents context sensitive resolution.
 *
 * For example:
 * ```
 * enum class Foo {
 *    FOO
 * }
 *
 * fun test(): Foo = FOO.<caret> // e.g. completion of `apply`
 * ```
 */
@Serializable
internal class QualifyContextSensitiveResolutionHandler(
    @Serializable(with = KotlinFqNameSerializer::class)
    private val fullyQualifiedName: FqName,
    private val exprOffset: Int,
) : SerializableInsertHandler {
    override fun handleInsert(
        context: InsertionContext,
        item: LookupElement
    ) {
        val beforeCaret = context.file.findElementAt(exprOffset)?.parent as? KtExpression ?: return
        val qualifiedExpression = beforeCaret.getQualifiedExpressionForSelector() ?: return

        val factory = KtPsiFactory(context.project)
        val targetElement = qualifiedExpression.getLeftMostReceiverExpression()
        val prefix = factory.createExpression(fullyQualifiedName.withRootPrefixIfNeeded(targetElement).asString())
        val replacedExpression = targetElement.prependDotQualifiedReceiver(prefix, factory)
        ShortenReferencesFacility.getInstance().shorten(replacedExpression)

        // Need to commit the PSI changes to the document for potential following insert handlers that modify the document
        PsiDocumentManager.getInstance(context.project).doPostponedOperationsAndUnblockDocument(context.document)
    }

    companion object {
        internal fun qualifyExpressionForContextSensitiveResolution(parameters: CompletionParameters): KotlinFirCompletionParameters? {
            val parentKtElement = parameters.position.parent as? KtElement ?: return null
            analyze(parentKtElement) {
                val fixedPosition = addQualifierIfNeeded(parameters.position) ?: return null
                return Corrected.fromFixedPosition(parameters, fixedPosition, CorrectionType.QUALIFIED_CONTEXT_SENSITIVE_RESOLUTION)
            }
        }
    }
}

internal fun LookupElementBuilder.qualifyContextSensitiveResolutionIfNecessary(positionContext: KotlinRawPositionContext): LookupElementBuilder {
    val fqName = positionContext.position.contextSensitiveResolutionFqn ?: return this
    return withChainedInsertHandler(QualifyContextSensitiveResolutionHandler(fqName.fqName, fqName.offset))
}

private var UserDataHolder.contextSensitiveResolutionFqn: FqNameWithOffset?
        by UserDataProperty(Key("QualifyContextSensitiveResolution.CONTEXT_SENSITIVE_RESOLUTION_FQN"))


private data class FqNameWithOffset(val fqName: FqName, val offset: Int)

@OptIn(KaExperimentalApi::class)
context(_: KaSession)
private fun addQualifierIfNeeded(position: PsiElement): PsiElement? {
    val fileCopy = position.containingFile.copy() as KtFile
    // Do not modify physical files! (copies should not be physical but can be due to bugs)
    // See: KTNB-1308
    if (fileCopy.isPhysical) return null
    val positionInCopy = PsiTreeUtil.findSameElementInCopy(position, fileCopy)
    val referenceExpr = positionInCopy.parent as? KtNameReferenceExpression ?: return null
    val offset = referenceExpr.startOffset

    // We want to check if context sensitive resolution was used before the `.` was inserted.
    // So we remove the selector in a copy and check if context sensitive resolution was used there.
    val qualifiedExpression = referenceExpr.getQualifiedExpressionForSelector() ?: return null
    val qualifiedExpressionCopy = qualifiedExpression.copied()
    val replaced = qualifiedExpression.replace(qualifiedExpression.receiverExpression)

    if (replaced !is KtSimpleNameExpression) return null

    analyze(fileCopy) {
        val reference = replaced.mainReference
        if (!reference.usesContextSensitiveResolution) return null

        val fqName = reference.resolveToSymbol()?.importableFqName?.parent() ?: return null

        val factory = KtPsiFactory(position.project)

        // Context sensitive resolution was used, so we add the full qualifier so that things still get resolved properly
        // after inserting the `.`.
        val fullyQualifiedExpression = factory.createExpression(fqName.asString() + "." + qualifiedExpressionCopy.text)
        val replacement = replaced.replaced(fullyQualifiedExpression) as? KtDotQualifiedExpression ?: return null

        val referenceElement = (replacement.selectorExpression as? KtNameReferenceExpression)?.getReferencedNameElement() ?: return null
        referenceElement.contextSensitiveResolutionFqn = FqNameWithOffset(fqName, offset)
        return referenceElement
    }
}
