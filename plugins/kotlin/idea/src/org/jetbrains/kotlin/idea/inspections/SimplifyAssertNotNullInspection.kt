// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractApplicabilityBasedInspection
import org.jetbrains.kotlin.idea.core.ShortenReferences
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.expressionComparedToNull
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.calls.model.isReallySuccess
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

class SimplifyAssertNotNullInspection : AbstractApplicabilityBasedInspection<KtCallExpression>(KtCallExpression::class.java) {

    override fun isApplicable(element: KtCallExpression): Boolean {
        if ((element.calleeExpression as? KtNameReferenceExpression)?.getReferencedName() != "assert") return false

        val arguments = element.valueArguments
        if (arguments.size != 1 && arguments.size != 2) return false

        val condition = arguments.first().getArgumentExpression() as? KtBinaryExpression ?: return false
        if (condition.operationToken != KtTokens.EXCLEQ) return false
        val value = condition.expressionComparedToNull() as? KtNameReferenceExpression ?: return false

        val prevDeclaration = findVariableDeclaration(element) ?: return false
        if (value.getReferencedNameAsName() != prevDeclaration.nameAsName) return false
        if (prevDeclaration.initializer == null) return false

        val resolvedCall = element.resolveToCall() ?: return false
        if (!resolvedCall.isReallySuccess()) return false
        val function = resolvedCall.resultingDescriptor as? FunctionDescriptor ?: return false
        if (function.importableFqName?.asString() != "kotlin.assert") return false

        if (arguments.size != 1) {
            if (extractMessage(element) == null) return false
        }
        return true
    }

    override fun inspectionText(element: KtCallExpression) = KotlinBundle.message("assert.should.be.replaced.with.operator")

    override val defaultFixText: String get() = KotlinBundle.message("replace.assert.with.operator")

    override fun fixText(element: KtCallExpression): String = if (element.valueArguments.size == 1) {
        KotlinBundle.message("replace.with.0.operator", "!!")
    } else {
        KotlinBundle.message("replace.with.error")
    }

    override fun applyTo(element: KtCallExpression, project: Project, editor: Editor?) {
        val declaration = findVariableDeclaration(element) ?: return
        val initializer = declaration.initializer ?: return
        val message = extractMessage(element)

        val commentSaver = CommentSaver(element)

        if (message == null) {
            val newInitializer = KtPsiFactory(project).createExpressionByPattern("$0!!", initializer)
            initializer.replace(newInitializer)
        } else {
            val newInitializer = KtPsiFactory(project).createExpressionByPattern("$0 ?: kotlin.error($1)", initializer, message)
            val result = initializer.replace(newInitializer)

            val qualifiedExpression = (result as KtBinaryExpression).right as KtDotQualifiedExpression
            ShortenReferences.DEFAULT.process(
                element.containingKtFile,
                qualifiedExpression.startOffset,
                (qualifiedExpression.selectorExpression as KtCallExpression).calleeExpression!!.endOffset
            )
        }

        element.delete()

        commentSaver.restore(declaration)

        if (editor != null) {
            val newInitializer = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(declaration)?.initializer ?: return
            val offset = if (message == null)
                newInitializer.endOffset
            else
                (newInitializer as KtBinaryExpression).operationReference.startOffset
            editor.moveCaret(offset)
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
