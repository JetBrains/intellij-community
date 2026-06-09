// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.intentions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiBasedModCommandAction
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringBundle
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.idea.codeinsight.utils.getLeftMostReceiverExpression
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

sealed class ConvertToScopeIntention(private val scopeFunction: ScopeFunction) : PsiBasedModCommandAction<KtExpression>(KtExpression::class.java) {
    enum class ScopeFunction(val functionName: String, val isParameterScope: Boolean) {
        ALSO(functionName = "also", isParameterScope = true),
        APPLY(functionName = "apply", isParameterScope = false),
        RUN(functionName = "run", isParameterScope = false),
        WITH(functionName = "with", isParameterScope = false);

        val receiver: String = if (isParameterScope) StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier else "this"
    }

    private data class RefactoringTargetAndItsValueExpression(
      val targetElement: PsiElement,
      val targetElementValue: PsiElement
    )

    private data class ScopedFunctionCallAndBlock(
      val scopeFunctionCall: KtExpression,
      val block: KtBlockExpression
    )

    private data class ConversionData(
      val firstTarget: PsiElement,
      val lastTarget: PsiElement,
      val refactoringTarget: RefactoringTargetAndItsValueExpression,
      val referencesToReplace: List<KtNameReferenceExpression>,
    )

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("convert.to.0", scopeFunction.functionName)

    override fun isElementApplicable(element: KtExpression, context: ActionContext): Boolean =
        prepareConversion(element, greedy = false, collectReferences = false) != null

    override fun getPresentation(context: ActionContext, element: KtExpression): Presentation =
        Presentation.of(familyName)

    override fun perform(context: ActionContext, element: KtExpression): ModCommand {
        val conversionData = prepareConversion(element, greedy = true, collectReferences = true)
            ?: return ModCommand.error(cannotConvertMessage())

        return ModCommand.psiUpdate(element) { _, updater ->
            applyConversion(conversionData, updater)
        }
    }

    private fun cannotConvertMessage(): @NlsContexts.Tooltip String = RefactoringBundle.getCannotRefactorMessage(
      JavaRefactoringBundle.message("refactoring.is.not.supported.in.the.current.context", familyName)
    )

    private fun KtExpression.childOfBlock(): KtExpression? =
        PsiTreeUtil.findFirstParent(this) {
            val parent = it.parent
            parent is KtBlockExpression || parent is KtValueArgument
        } as? KtExpression

    private fun KtExpression.tryGetExpressionToApply(referenceName: String): KtExpression? {
        val childOfBlock: KtExpression = childOfBlock() ?: return null

        return if (childOfBlock is KtProperty || childOfBlock.isTarget(referenceName)) childOfBlock else null
    }

    private fun prepareConversion(element: KtExpression, greedy: Boolean, collectReferences: Boolean): ConversionData? {
        val invalidElementToRefactoring = when (element) {
            is KtProperty -> !element.isLocal
            is KtCallExpression -> false
            is KtDotQualifiedExpression -> false
            else -> true
        }
        if (invalidElementToRefactoring) return null

        val (referenceElement, referenceName) = element.tryExtractReferenceName() ?: return null
        val expressionToApply = element.tryGetExpressionToApply(referenceName) ?: return null
        val (firstTarget, lastTarget) = expressionToApply.collectTargetElementsRange(referenceName, greedy) ?: return null

        val refactoringTarget = tryGetFirstElementToRefactoring(expressionToApply, firstTarget, lastTarget, referenceElement) ?: return null
        val referencesToReplace = if (collectReferences) {
            collectReferencesToReplace(referenceElement, refactoringTarget.targetElementValue, lastTarget)
        } else {
            emptyList()
        }

        return ConversionData(firstTarget, lastTarget, refactoringTarget, referencesToReplace)
    }

    private fun applyConversion(conversionData: ConversionData, updater: ModPsiUpdater) {
        val firstTarget = updater.getWritable(conversionData.firstTarget)
        val lastTarget = updater.getWritable(conversionData.lastTarget)
        val targetElement = updater.getWritable(conversionData.refactoringTarget.targetElement)
        val targetElementValue = updater.getWritable(conversionData.refactoringTarget.targetElementValue)
        val referencesToReplace = conversionData.referencesToReplace.map { updater.getWritable(it) }
        val psiFactory = KtPsiFactory(firstTarget.project)

        val (scopeFunctionCall, block) = createScopeFunctionCall(
            psiFactory,
            targetElement
        ) ?: run {
            updater.cancel(cannotConvertMessage())
            return
        }

        replaceReferences(referencesToReplace, psiFactory)

        block.addRange(targetElementValue, lastTarget)

        if (!scopeFunction.isParameterScope) {
            removeRedundantThisQualifiers(block)
        }

        with(firstTarget) {
            parent.addBefore(scopeFunctionCall, this)
            parent.deleteChildRange(this, lastTarget)
        }
    }

    private fun removeRedundantThisQualifiers(block: KtBlockExpression) {
        val thisDotSomethingExpressions = block.collectDescendantsOfType<KtDotQualifiedExpression> {
          it.receiverExpression is KtThisExpression && it.selectorExpression !== null
        }

        thisDotSomethingExpressions.forEach { thisDotSomethingExpression ->
            thisDotSomethingExpression.selectorExpression?.let { selector ->
                thisDotSomethingExpression.replace(selector)
            }
        }
    }

    private fun tryGetFirstElementToRefactoring(
      expressionToApply: KtExpression,
      firstTarget: PsiElement,
      lastTarget: PsiElement,
      referenceElement: PsiElement
    ): RefactoringTargetAndItsValueExpression? {
        val property = expressionToApply.prevProperty()

        val propertyOrFirst = when (scopeFunction) {
            ScopeFunction.ALSO, ScopeFunction.APPLY -> property
            ScopeFunction.RUN, ScopeFunction.WITH -> firstTarget
        } ?: return null

        val isCorrectFirstOrProperty = when (scopeFunction) {
            ScopeFunction.ALSO, ScopeFunction.APPLY -> propertyOrFirst is KtProperty && propertyOrFirst.name !== null && propertyOrFirst.initializer !== null
            ScopeFunction.RUN -> propertyOrFirst is KtDotQualifiedExpression
            ScopeFunction.WITH -> propertyOrFirst is KtDotQualifiedExpression
        }

        if (!isCorrectFirstOrProperty) return null

        val targetElementValue =
            property?.nextSibling?.takeIf { it.parent == referenceElement.parent && it.textOffset < lastTarget.textOffset } ?: firstTarget
        return RefactoringTargetAndItsValueExpression(propertyOrFirst, targetElementValue)
    }

    private fun collectReferencesToReplace(
      element: PsiElement,
      firstTarget: PsiElement,
      lastTarget: PsiElement,
    ): List<KtNameReferenceExpression> {
        return PsiTreeUtil.getElementsOfRange(firstTarget, lastTarget).flatMap { rangeElement ->
            rangeElement.collectDescendantsOfType<KtNameReferenceExpression> { reference ->
                reference.mainReference.resolve() == element
            }
        }
    }

    private fun replaceReferences(referencesToReplace: List<KtNameReferenceExpression>, psiFactory: KtPsiFactory) {
        referencesToReplace.forEach { referenceInRange ->
            val replacement = if (scopeFunction.isParameterScope) {
                psiFactory.createSimpleName(scopeFunction.receiver)
            } else {
                psiFactory.createThisExpression()
            }
            referenceInRange.replace(replacement)
        }
    }

    private fun KtExpression.tryExtractReferenceName(): Pair<PsiElement, String>? {
        return when (scopeFunction) {
            ScopeFunction.ALSO, ScopeFunction.APPLY -> {
                val property = prevProperty()
                val name = property?.name
                if (name !== null) property to name else null
            }
            ScopeFunction.RUN, ScopeFunction.WITH -> {
                val receiver = safeAs<KtDotQualifiedExpression>()?.getLeftMostReceiverExpression() as? KtNameReferenceExpression
                val declaration = receiver?.mainReference?.resolve()?.takeUnless { it is PsiPackage } ?: return null
                val selector = receiver.getQualifiedExpressionForReceiver()?.selectorExpression
                    ?.let { it.safeAs<KtCallExpression>()?.calleeExpression ?: it } as? KtNameReferenceExpression
                if (selector?.mainReference?.resolve() is KtClassOrObject) return null
                declaration to receiver.getReferencedName()
            }
        }
    }

    private fun KtExpression.collectTargetElementsRange(referenceName: String, greedy: Boolean): Pair<PsiElement, PsiElement>? {
        return when (scopeFunction) {
            ScopeFunction.ALSO, ScopeFunction.APPLY -> {
                val firstTarget = this as? KtProperty ?: this.prevProperty() ?: this

                val lastTargetSequence = firstTarget.collectTargetElements(referenceName, forward = true)

                val lastTarget = if (firstTarget === this)
                    if (greedy) lastTargetSequence.lastOrNull()
                    else lastTargetSequence.firstOrNull()
                else
                    if (greedy) lastTargetSequence.lastWithPersistedElementOrNull(elementShouldPersist = this)
                    else lastTargetSequence.firstOrNull { this === it }

                if (lastTarget !== null) firstTarget to lastTarget else null
            }
            ScopeFunction.RUN, ScopeFunction.WITH -> {

                val firstTarget = collectTargetElements(referenceName, forward = false).lastOrNull() ?: this

                val lastTarget =
                    if (greedy) collectTargetElements(referenceName, forward = true).lastOrNull() ?: this
                    else this

                firstTarget to lastTarget
            }
        }
    }

    private fun KtExpression.collectTargetElements(referenceName: String, forward: Boolean): Sequence<PsiElement> =
        siblings(forward, withItself = false)
            .filter { it !is PsiWhiteSpace && it !is PsiComment && !(it is LeafPsiElement && it.elementType == KtTokens.SEMICOLON) }
            .takeWhile { it.isTarget(referenceName) }

    private fun PsiElement.isTarget(referenceName: String): Boolean {
        when (this) {
            is KtDotQualifiedExpression -> {
                val callExpr = callExpression ?: return false
                if (callExpr.lambdaArguments.isNotEmpty() ||
                    callExpr.valueArguments.any { it.text == scopeFunction.receiver }
                ) return false

                val leftMostReceiver = getLeftMostReceiverExpression()
                if (leftMostReceiver.text != referenceName) return false

                if (leftMostReceiver.mainReference?.resolve() is PsiClass) return false
            }
            is KtCallExpression -> {
                val valueArguments = this.valueArguments
                if (valueArguments.none { it.getArgumentExpression()?.text == referenceName }) return false
                if (lambdaArguments.isNotEmpty() || valueArguments.any { it.text == scopeFunction.receiver }) return false
            }
            is KtBinaryExpression -> {
                val left = this.left ?: return false
                val right = this.right ?: return false
                if (left !is KtDotQualifiedExpression && left !is KtCallExpression
                    && right !is KtDotQualifiedExpression && right !is KtCallExpression
                ) return false
                if ((left is KtDotQualifiedExpression || left is KtCallExpression) && !left.isTarget(referenceName)) return false
                if ((right is KtDotQualifiedExpression || right is KtCallExpression) && !right.isTarget(referenceName)) return false
            }
            else -> return false
        }

        return !anyDescendantOfType<KtNameReferenceExpression> { it.text == scopeFunction.receiver }
    }

    private fun KtExpression.prevProperty(): KtProperty? = childOfBlock()
        ?.siblings(forward = false, withItself = true)
        ?.firstOrNull { it is KtProperty && it.isLocal } as? KtProperty

    private fun createScopeFunctionCall(factory: KtPsiFactory, element: PsiElement): ScopedFunctionCallAndBlock? {
        val scopeFunctionName = scopeFunction.functionName
        val (scopeFunctionCall, callExpression) = when (scopeFunction) {
            ScopeFunction.ALSO, ScopeFunction.APPLY -> {
                if (element !is KtProperty) return null
                val propertyName = element.name ?: return null
                val initializer = element.initializer ?: return null

                val initializerPattern = when (initializer) {
                    is KtDotQualifiedExpression, is KtCallExpression, is KtConstantExpression, is KtParenthesizedExpression -> initializer.text
                    else -> "(${initializer.text})"
                }

                val property = factory.createProperty(
                    name = propertyName,
                    type = element.typeReference?.text,
                    isVar = element.isVar,
                    initializer = "$initializerPattern.$scopeFunctionName {}"
                )
                val callExpression = (property.initializer as? KtDotQualifiedExpression)?.callExpression ?: return null
                property to callExpression
            }
            ScopeFunction.RUN -> {
                if (element !is KtDotQualifiedExpression) return null
                val scopeFunctionCall = factory.createExpressionByPattern(
                    "$0.$scopeFunctionName {}",
                    element.getLeftMostReceiverExpression()
                ) as? KtQualifiedExpression ?: return null
                val callExpression = scopeFunctionCall.callExpression ?: return null
                scopeFunctionCall to callExpression
            }
            ScopeFunction.WITH -> {
                if (element !is KtDotQualifiedExpression) return null

                val scopeFunctionCall = factory.createExpressionByPattern(
                    "$scopeFunctionName($0) {}",
                    element.getLeftMostReceiverExpression()
                ) as? KtCallExpression ?: return null
                scopeFunctionCall to scopeFunctionCall
            }
        }

        val body = callExpression.lambdaArguments
            .firstOrNull()
            ?.getLambdaExpression()
            ?.bodyExpression
            ?: return null

        return ScopedFunctionCallAndBlock(scopeFunctionCall, body)
    }
}

private fun Sequence<PsiElement>.lastWithPersistedElementOrNull(elementShouldPersist: KtExpression): PsiElement? {
    var lastElement: PsiElement? = null
    var checked = false

    for (element in this) {
        checked = checked || (element === elementShouldPersist)
        lastElement = element
    }

    return if (checked) lastElement else null
}

class ConvertToAlsoIntention : ConvertToScopeIntention(ScopeFunction.ALSO)
class ConvertToApplyIntention : ConvertToScopeIntention(ScopeFunction.APPLY)
class ConvertToRunIntention : ConvertToScopeIntention(ScopeFunction.RUN)
class ConvertToWithIntention : ConvertToScopeIntention(ScopeFunction.WITH)