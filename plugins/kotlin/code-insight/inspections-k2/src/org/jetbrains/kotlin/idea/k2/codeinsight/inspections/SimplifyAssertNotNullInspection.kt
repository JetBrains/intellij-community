// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.IntentionName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.shortenReferences
import org.jetbrains.kotlin.idea.base.psi.expressionComparedToNull
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

internal class SimplifyAssertNotNullInspection :
    KotlinApplicableInspectionBase.Simple<KtCallExpression, SimplifyAssertNotNullInspection.Context>() {

    internal data class Context(
        val declaration: KtVariableDeclaration,
        val message: KtExpression?
    )

    override fun getProblemDescription(element: KtCallExpression, context: Context) =
        KotlinBundle.message("assert.should.be.replaced.with.operator")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {
        override fun visitCallExpression(expression: KtCallExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun KaSession.prepareContext(element: KtCallExpression): Context? {

        val calleeExpression = element.calleeExpression as? KtNameReferenceExpression ?: return null
        val functionSymbol = calleeExpression.resolveToCall()?.successfulFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol
        if (functionSymbol?.callableId.toString() != "kotlin/assert") return null

        val prevDeclaration = findVariableDeclaration(element) ?: return null

        return Context(
            declaration = prevDeclaration,
            message = extractMessage(element)
        )
    }

    override fun isApplicableByPsi(element: KtCallExpression): Boolean {
        if ((element.calleeExpression as? KtNameReferenceExpression)?.getReferencedName() != "assert") return false
        val arguments = element.valueArguments
        if (arguments.size != 1 && arguments.size != 2) return false

        val condition = arguments.first().getArgumentExpression() as? KtBinaryExpression ?: return false
        if (condition.operationToken != KtTokens.EXCLEQ) return false
        val value = condition.expressionComparedToNull() as? KtNameReferenceExpression ?: return false

        val prevDeclaration = findVariableDeclaration(element) ?: return false
        if (value.getReferencedNameAsName() != prevDeclaration.nameAsName) return false
        if (prevDeclaration.initializer == null) return false


        if (arguments.size != 1) {
            if (extractMessage(element) == null) return false
        }
        return true
    }

    override fun createQuickFix(
        element: KtCallExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtCallExpression> = object : KotlinModCommandQuickFix<KtCallExpression>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("replace.assert.with.operator")

        override fun getName(): @IntentionName String = if (context.message == null) {
            KotlinBundle.message("replace.with.0.operator", "!!")
        } else {
            KotlinBundle.message("replace.with.error")
        }

        override fun applyFix(
            project: Project,
            element: KtCallExpression,
            updater: ModPsiUpdater,
        ) {
            val declaration = updater.getWritable(context.declaration)
            val initializer = declaration.initializer ?: return
            val message = context.message?.let { updater.getWritable(it) }

            val commentSaver = CommentSaver(element)

            if (message == null) {
                val newInitializer = KtPsiFactory(project).createExpressionByPattern("$0!!", initializer)
                initializer.replace(newInitializer)
            } else {
                val newInitializer = KtPsiFactory(project).createExpressionByPattern("$0 ?: kotlin.error($1)", initializer, message)
                val result = initializer.replace(newInitializer)

                shortenReferences((result as KtBinaryExpression).right as KtElement)
            }

            element.delete()
            commentSaver.restore(declaration)

            val newInitializer = declaration.initializer ?: return
            if (message == null) {
                updater.moveCaretTo(newInitializer.endOffset)
            } else {
                updater.moveCaretTo((newInitializer as KtBinaryExpression).operationReference.startOffset)
            }
        }
    }

    private fun findVariableDeclaration(element: KtCallExpression): KtVariableDeclaration? {
        if (element.parent !is KtBlockExpression) return null
        return element.siblings(forward = false, withItself = false).firstIsInstanceOrNull<KtExpression>() as? KtVariableDeclaration
    }

    private fun extractMessage(element: KtCallExpression): KtExpression? {
        val arguments = element.valueArguments
        if (arguments.size != 2) return null
        return (arguments[1].getArgumentExpression() as? KtLambdaExpression)
            ?.bodyExpression
            ?.statements
            ?.singleOrNull()
    }
}
