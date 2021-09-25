// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.analysis.analyzeAsReplacement
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.intentions.callExpression
import org.jetbrains.kotlin.idea.intentions.canBeReplacedWithInvokeCall
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.resolvedCallUtil.getImplicitReceiverValue
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameUnsafe
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

abstract class ReplaceCallFix(
    expression: KtQualifiedExpression,
    private val operation: String,
    private val notNullNeeded: Boolean = false
) : KotlinQuickFixAction<KtQualifiedExpression>(expression) {

    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        val element = element ?: return false
        return element.selectorExpression != null
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        replace(element, project, editor)
    }

    protected fun replace(element: KtQualifiedExpression?, project: Project, editor: Editor?): KtExpression? {
        val selectorExpression = element?.selectorExpression ?: return null
        val elvis = element.elvisOrEmpty(notNullNeeded)
        val betweenReceiverAndOperation = element.elementsBetweenReceiverAndOperation().joinToString(separator = "") { it.text }
        val newExpression = KtPsiFactory(element).createExpressionByPattern(
            "$0$betweenReceiverAndOperation$operation$1$elvis",
            element.receiverExpression,
            selectorExpression,
        )

        val replacement = element.replace(newExpression)
        if (elvis.isNotEmpty()) {
            replacement.moveCaretToEnd(editor, project)
        }

        return replacement as? KtExpression
    }

    private fun KtQualifiedExpression.elementsBetweenReceiverAndOperation(): List<PsiElement> {
        val receiver = receiverExpression
        val operation = operationTokenNode as? PsiElement ?: return emptyList()
        val start = receiver.nextSibling?.takeIf { it != operation } ?: return emptyList()
        val end = operation.prevSibling?.takeIf { it != receiver } ?: return emptyList()
        return PsiTreeUtil.getElementsOfRange(start, end)
    }
}

class ReplaceImplicitReceiverCallFix(
    expression: KtExpression,
    private val notNullNeeded: Boolean
) : KotlinQuickFixAction<KtExpression>(expression) {
    override fun getFamilyName() = text

    override fun getText() = KotlinBundle.message("replace.with.safe.this.call")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val elvis = element.elvisOrEmpty(notNullNeeded)
        val newExpression = KtPsiFactory(element).createExpressionByPattern("this?.$0$elvis", element)
        val replacement = element.replace(newExpression)
        if (elvis.isNotEmpty()) {
            replacement.moveCaretToEnd(editor, project)
        }
    }
}

class ReplaceWithSafeCallFix(
    expression: KtDotQualifiedExpression,
    notNullNeeded: Boolean
) : ReplaceCallFix(expression, "?.", notNullNeeded) {

    override fun getText() = KotlinBundle.message("replace.with.safe.call")

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val psiElement = diagnostic.psiElement
            val qualifiedExpression = psiElement.parent as? KtDotQualifiedExpression
            if (qualifiedExpression != null) {
                val call = qualifiedExpression.callExpression
                if (call != null) {
                    val context = qualifiedExpression.analyze(BodyResolveMode.PARTIAL)
                    val ktPsiFactory = KtPsiFactory(psiElement)
                    val safeQualifiedExpression = ktPsiFactory.createExpressionByPattern(
                        "$0?.$1", qualifiedExpression.receiverExpression, call,
                        reformat = false
                    )
                    val newContext = safeQualifiedExpression.analyzeAsReplacement(qualifiedExpression, context)
                    if (safeQualifiedExpression.getResolvedCall(newContext)?.canBeReplacedWithInvokeCall() == true) {
                        return ReplaceInfixOrOperatorCallFix(call, call.shouldHaveNotNullType())
                    }
                }
                return ReplaceWithSafeCallFix(qualifiedExpression, qualifiedExpression.shouldHaveNotNullType())
            } else {
                if (psiElement !is KtNameReferenceExpression) return null
                if (psiElement.getResolvedCall(psiElement.analyze())?.getImplicitReceiverValue() != null) {
                    val expressionToReplace: KtExpression = psiElement.parent as? KtCallExpression ?: psiElement
                    return ReplaceImplicitReceiverCallFix(expressionToReplace, expressionToReplace.shouldHaveNotNullType())
                }
                return null
            }
        }
    }
}

class ReplaceWithSafeCallForScopeFunctionFix(
    expression: KtDotQualifiedExpression,
    notNullNeeded: Boolean
) : ReplaceCallFix(expression, "?.", notNullNeeded) {

    override fun getText() = KotlinBundle.message("replace.scope.function.with.safe.call")

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): KotlinQuickFixAction<KtExpression>? {
            val element = diagnostic.psiElement
            val scopeFunctionLiteral = element.getStrictParentOfType<KtFunctionLiteral>() ?: return null
            val scopeCallExpression = scopeFunctionLiteral.getStrictParentOfType<KtCallExpression>() ?: return null
            val scopeDotQualifiedExpression = scopeCallExpression.getStrictParentOfType<KtDotQualifiedExpression>() ?: return null

            val context = scopeCallExpression.analyze()
            val scopeFunctionLiteralDescriptor = context[BindingContext.FUNCTION, scopeFunctionLiteral] ?: return null
            val scopeFunctionKind = scopeCallExpression.scopeFunctionKind(context) ?: return null

            val internalReceiver = (element.parent as? KtDotQualifiedExpression)?.receiverExpression
            val internalReceiverDescriptor = internalReceiver.getResolvedCall(context)?.candidateDescriptor
            val internalResolvedCall = (element.getParentOfType<KtElement>(strict = false))?.getResolvedCall(context)
                ?: return null

            when (scopeFunctionKind) {
                ScopeFunctionKind.WITH_PARAMETER -> {
                    if (internalReceiverDescriptor != scopeFunctionLiteralDescriptor.valueParameters.singleOrNull()) {
                        return null
                    }
                }
                ScopeFunctionKind.WITH_RECEIVER -> {
                    if (internalReceiverDescriptor != scopeFunctionLiteralDescriptor.extensionReceiverParameter &&
                        internalResolvedCall.getImplicitReceiverValue() == null
                    ) {
                        return null
                    }
                }
            }

            return ReplaceWithSafeCallForScopeFunctionFix(
                scopeDotQualifiedExpression, scopeDotQualifiedExpression.shouldHaveNotNullType()
            )
        }

        private fun KtCallExpression.scopeFunctionKind(context: BindingContext): ScopeFunctionKind? {
            val methodName = getResolvedCall(context)?.resultingDescriptor?.fqNameUnsafe?.asString()
            return ScopeFunctionKind.values().firstOrNull { kind -> kind.names.contains(methodName) }
        }

        private enum class ScopeFunctionKind(vararg val names: String) {
            WITH_PARAMETER("kotlin.let", "kotlin.also"),
            WITH_RECEIVER("kotlin.apply", "kotlin.run")
        }
    }
}

class ReplaceWithDotCallFix(
    expression: KtSafeQualifiedExpression,
    private val callChainCount: Int = 0
) : ReplaceCallFix(expression, "."), CleanupFix {
    override fun getText() = KotlinBundle.message("replace.with.dot.call")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        var replaced = replace(element, project, editor) ?: return
        repeat(callChainCount) {
            val parent = replaced.getQualifiedExpressionForReceiver() as? KtSafeQualifiedExpression ?: return
            replaced = replace(parent, project, editor) ?: return
        }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val qualifiedExpression = diagnostic.psiElement.getParentOfType<KtSafeQualifiedExpression>(strict = false) ?: return null

            var parent = qualifiedExpression.getQualifiedExpressionForReceiver() as? KtSafeQualifiedExpression
            var callChainCount = 0
            if (parent != null) {
                val bindingContext = qualifiedExpression.analyze(BodyResolveMode.PARTIAL_WITH_DIAGNOSTICS)
                while (parent is KtQualifiedExpression) {
                    val compilerReports = bindingContext.diagnostics.forElement(parent.operationTokenNode as PsiElement)
                    if (compilerReports.none { it.factory == Errors.UNNECESSARY_SAFE_CALL }) break
                    callChainCount++
                    parent = parent.getQualifiedExpressionForReceiver() as? KtSafeQualifiedExpression
                }
            }

            return ReplaceWithDotCallFix(qualifiedExpression, callChainCount)
        }
    }
}

