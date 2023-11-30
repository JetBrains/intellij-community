// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.prevLeafs
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.types.typeUtil.isPrimitiveNumberType

class UnusedUnaryOperatorInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitorVoid = object : KtVisitorVoid() {
        override fun visitPrefixExpression(expression: KtPrefixExpression) {
            check(expression)
        }

        override fun visitBinaryExpression(expression: KtBinaryExpression) {
            if (expression.parent is KtBinaryExpression) return
            var left = expression.left
            while (left is KtBinaryExpression) {
                left = left.left
            }
            val prefix = left as? KtPrefixExpression ?: return
            check(prefix, expression)
        }

        private fun check(prefix: KtPrefixExpression, parentBinary: KtBinaryExpression? = null) {
            if (prefix.baseExpression == null) return
            val operationToken = prefix.operationToken
            if (operationToken != KtTokens.PLUS && operationToken != KtTokens.MINUS) return

            // Hack to fix KTIJ-196 (unstable `USED_AS_EXPRESSION` marker for KtAnnotationEntry).
            // Do not remove this line without manually checking the code from KTIJ-196 in a real project,
            // because the current autotests may not be able to catch the regression.
            if (prefix.isInAnnotationEntry) return

            val context = prefix.safeAnalyzeNonSourceRootCode()
            if (context == BindingContext.EMPTY || isUsedAsExpression(prefix, parentBinary, context)) return
            val operatorDescriptor = prefix.operationReference.getResolvedCall(context)?.resultingDescriptor as? DeclarationDescriptor ?: return
            if (!KotlinBuiltIns.isUnderKotlinPackage(operatorDescriptor)) return

            holder.registerProblem(prefix, KotlinBundle.message("unused.unary.operator"), *createFixes(parentBinary ?: prefix, context))
        }
    }

    private fun isUsedAsExpression(prefix: KtPrefixExpression, parentBinary: KtBinaryExpression?, context: BindingContext): Boolean {
        if (prefix.operationToken == KtTokens.PLUS) {
            // consider the unary plus operator unused in cases like `x -+ 1`
            val prev = prefix.getPrevSiblingIgnoringWhitespaceAndComments()
            if (prev is KtOperationReferenceExpression && prev.parent is KtBinaryExpression) return false
        }
        return (parentBinary ?: prefix).isUsedAsExpression(context)
    }

    private fun createFixes(expression: KtExpression, context: BindingContext): Array<LocalQuickFix> {
        val fixes = mutableListOf<LocalQuickFix>(RemoveUnaryOperatorFix())

        val prevLeaf = expression.getPrevLeafIgnoringWhitespaceAndComments()
        if (prevLeaf != null) {
            val type = expression.getType(context)
            val prevType = prevLeaf.getStrictParentOfType<KtExpression>()?.getType(context)
            if (type != null && prevType != null &&
                (type == prevType || type.isPrimitiveNumberType() && prevType.isPrimitiveNumberType())
            ) {
                fixes.add(MoveUnaryOperatorToPreviousLineFix())
            }
        }

        return fixes.toTypedArray()
    }

    private class RemoveUnaryOperatorFix : LocalQuickFix {
        override fun getName() = KotlinBundle.message("remove.unary.operator.fix.text")

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val prefixExpression = descriptor.psiElement as? KtPrefixExpression ?: return
            val baseExpression = prefixExpression.baseExpression ?: return
            prefixExpression.replace(baseExpression)
        }
    }

    private class MoveUnaryOperatorToPreviousLineFix : LocalQuickFix, HighPriorityAction {
        override fun getName(): String = KotlinBundle.message("move.unary.operator.to.previous.line.fix.text")

        override fun getFamilyName(): String = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val prefixExpression = descriptor.psiElement as? KtPrefixExpression ?: return
            val baseExpression = prefixExpression.baseExpression ?: return
            val prevLeafStartOffset = prefixExpression.getPrevLeafIgnoringWhitespaceAndComments()?.startOffset ?: return
            val prefixEndOffset = prefixExpression.endOffset

            val editor = prefixExpression.findExistingEditor() ?: return
            val document = editor.document
            val documentManager = PsiDocumentManager.getInstance(project)
            val file = prefixExpression.containingFile

            prefixExpression.replace(baseExpression)
            documentManager.doPostponedOperationsAndUnblockDocument(document)

            document.insertString(prevLeafStartOffset + 1, " ${prefixExpression.operationReference.text}")
            documentManager.commitDocument(document)

            CodeStyleManager.getInstance(project).adjustLineIndent(file, TextRange(prevLeafStartOffset, prefixEndOffset))
        }
    }
}

private val KtPrefixExpression.isInAnnotationEntry: Boolean
    get() = parentsWithSelf.takeWhile { it is KtExpression }.last().parent?.parent?.parent is KtAnnotationEntry

private fun KtExpression.getPrevLeafIgnoringWhitespaceAndComments(): PsiElement? =
    prevLeafs.firstOrNull { it !is PsiWhiteSpace && it !is PsiComment }
