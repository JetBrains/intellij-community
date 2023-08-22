// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.intention.FileModifier
import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection.*
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
import com.intellij.codeInspection.ProblemHighlightType.INFORMATION
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.base.psi.getLineStartOffset
import org.jetbrains.kotlin.idea.base.psi.isOneLiner
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.idea.inspections.Action.*
import org.jetbrains.kotlin.idea.intentions.isComplexInitializer
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.countUsages
import org.jetbrains.kotlin.idea.intentions.loopToCallChain.previousStatement
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*

class MoveVariableDeclarationIntoWhenInspection : AbstractKotlinInspection(), CleanupLocalInspectionTool {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        whenExpressionVisitor(fun(expression: KtWhenExpression) {
            val subjectExpression = expression.subjectExpression ?: return
            val property = expression.findDeclarationNear() ?: return
            val identifier = property.nameIdentifier ?: return
            val initializer = property.initializer ?: return
            if (initializer.isComplexInitializer()) return

            val action = property.action(expression)
            if (action == NOTHING) return
            if (action == MOVE && !property.isOneLiner()) return

            holder.registerProblemWithoutOfflineInformation(
                property,
                action.description,
                isOnTheFly,
                highlightType(action, expression, property),
                TextRange.from(identifier.startOffsetInParent, identifier.textLength),
                action.createFix(subjectExpression.createSmartPointer())
            )
        })
}

private fun highlightType(action: Action, whenExpression: KtWhenExpression, property: KtProperty): ProblemHighlightType = when (action) {
    INLINE -> INFORMATION
    MOVE -> {
        val file = whenExpression.containingFile
        val lineStartOffset = whenExpression.containingKtFile.getLineStartOffset(whenExpression.getLineNumber(), skipWhitespace = false)
        if (file != null && lineStartOffset != null) {
            val newWhenLength = (whenExpression.startOffset - lineStartOffset) + "when (".length + property.text.length + ") {".length
            val rightMargin = CodeStyle.getSettings(file).getRightMargin(file.language)
            if (newWhenLength > rightMargin) INFORMATION else GENERIC_ERROR_OR_WARNING
        } else {
            GENERIC_ERROR_OR_WARNING
        }
    }

    NOTHING -> error("Illegal action")
}

private enum class Action {
    NOTHING,
    MOVE,
    INLINE;

    val description: String
        get() = when (this) {
            MOVE -> KotlinBundle.message("variable.declaration.could.be.moved.into.when")
            INLINE -> KotlinBundle.message("variable.declaration.could.be.inlined")
            NOTHING -> KotlinBundle.message("nothing.to.do")
        }

    fun createFix(subjectExpressionPointer: SmartPsiElementPointer<KtExpression>): VariableDeclarationIntoWhenFix = when (this) {
        MOVE -> VariableDeclarationIntoWhenFix(KotlinBundle.message("move.variable.declaration.into.when"), subjectExpressionPointer) { it }
        INLINE -> VariableDeclarationIntoWhenFix(KotlinBundle.message("inline.variable"), subjectExpressionPointer) { it.initializer }
        else -> error("Illegal action")
    }
}

private fun KtProperty.action(element: KtElement): Action = when (val elementUsages = countUsages(element)) {
    countUsages() -> if (elementUsages == 1) INLINE else MOVE
    else -> NOTHING
}

private fun KtWhenExpression.findDeclarationNear(): KtProperty? {
    val previousProperty = previousStatement() as? KtProperty
        ?: previousPropertyFromParent()
        ?: return null
    return previousProperty.takeIf { !it.isVar && it.hasInitializer() && it.nameIdentifier?.text == subjectExpression?.text }
}

private tailrec fun KtExpression.previousPropertyFromParent(): KtProperty? {
    val parentExpression = parent as? KtExpression ?: return null
    if (this != when (parentExpression) {
            is KtProperty -> parentExpression.initializer
            is KtReturnExpression -> parentExpression.returnedExpression
            is KtBinaryExpression -> parentExpression.left
            is KtUnaryExpression -> parentExpression.baseExpression
            else -> null
        }
    ) return null

    return parentExpression.previousStatement() as? KtProperty ?: parentExpression.previousPropertyFromParent()
}

private class VariableDeclarationIntoWhenFix(
    @Nls private val actionName: String,
    private val subjectExpressionPointer: SmartPsiElementPointer<KtExpression>,
    @SafeFieldForPreview private val transform: (KtProperty) -> KtExpression?
) : LocalQuickFix {
    override fun getFileModifierForPreview(target: PsiFile): FileModifier {
        return VariableDeclarationIntoWhenFix(
            actionName,
            PsiTreeUtil
                .findSameElementInCopy(subjectExpressionPointer.element, target)
                .createSmartPointer(),
            transform
        )
    }

    override fun getName() = actionName

    override fun getFamilyName() = name

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val property = descriptor.psiElement as? KtProperty ?: return
        val subjectExpression = subjectExpressionPointer.element ?: return
        val newElement = property.copy() as? KtProperty ?: return
        val toReplace = transform(newElement) ?: return

        val tailComments = newElement.allChildren.toList().takeLastWhile { it is PsiWhiteSpace || it is PsiComment }
        if (tailComments.isNotEmpty()) {
            val leftBrace = subjectExpression.siblings(withItself = false).firstOrNull { it.node.elementType == KtTokens.LBRACE }
            if (leftBrace != null) {
                tailComments.reversed().forEach {
                    subjectExpression.parent.addAfter(it, leftBrace)
                    it.delete()
                }
            }
        }
        val docComment = newElement.docComment
        if (docComment != null) {
            val whenExpression = subjectExpression.parent
            whenExpression.parent.addBefore(docComment, whenExpression)
            docComment.delete()
        }

        val resultElement = subjectExpression.replace(toReplace)
        property.parent.deleteChildRange(property, property.nextSibling as? PsiWhiteSpace ?: property)

        val editor = resultElement.findExistingEditor() ?: return
        editor.moveCaret((resultElement as? KtProperty)?.nameIdentifier?.startOffset ?: resultElement.startOffset)
    }
}
