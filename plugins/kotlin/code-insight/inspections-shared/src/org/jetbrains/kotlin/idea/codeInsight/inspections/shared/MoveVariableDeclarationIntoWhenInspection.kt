// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.application.options.CodeStyle
import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.base.psi.getLineStartOffset
import org.jetbrains.kotlin.idea.base.psi.isOneLiner
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isComplexInitializer
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.psi.whenExpressionVisitor

internal class MoveVariableDeclarationIntoWhenInspection : AbstractKotlinInspection(), CleanupLocalInspectionTool {

    private data class Context(
        val action: Action,
        val subjectExpression: SmartPsiElementPointer<KtExpression>
    )

    private enum class Action(val description: @InspectionMessage String) {
        NOTHING( KotlinBundle.message("nothing.to.do")),
        MOVE(KotlinBundle.message("variable.declaration.could.be.moved.into.when")),
        INLINE(KotlinBundle.message("variable.declaration.could.be.inlined"))
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
      whenExpressionVisitor(fun(expression: KtWhenExpression) {
        val subjectExpression = expression.subjectExpression
        if (subjectExpression == null) return
        val property = expression.findDeclarationNear() ?: return
        val identifier = property.nameIdentifier ?: return
        val initializer = property.initializer ?: return
        if (initializer.isComplexInitializer()) return

        val action = property.action(expression)
        if (action == Action.NOTHING) return
        if (action == Action.MOVE && !property.isOneLiner()) return

        holder.registerProblemWithoutOfflineInformation(
          property,
          action.description,
          isOnTheFly,
          highlightType(action, expression, property),
          TextRange.from(identifier.startOffsetInParent, identifier.textLength),
          VariableDeclarationIntoWhenFix(Context(action, subjectExpression.createSmartPointer()))
        )
      })

    private fun highlightType(action: Action, whenExpression: KtWhenExpression, property: KtProperty): ProblemHighlightType = when (action) {
        Action.INLINE -> ProblemHighlightType.INFORMATION
        Action.MOVE -> {
            val file = whenExpression.containingFile
            val lineStartOffset = whenExpression.containingKtFile.getLineStartOffset(whenExpression.getLineNumber(), skipWhitespace = false)
            if (file != null && lineStartOffset != null) {
                val newWhenLength = (whenExpression.startOffset - lineStartOffset) + "when (".length + property.text.length + ") {".length
                val rightMargin = CodeStyle.getSettings(file).getRightMargin(file.language)
                if (newWhenLength > rightMargin) ProblemHighlightType.INFORMATION else ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            } else {
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            }
        }
        Action.NOTHING -> error("Illegal action")
    }

    private fun KtProperty.action(element: KtElement): Action {
        val usages = countUsages()
        val elementUsages = countUsages(element)
        return when (elementUsages) {
            usages -> if (elementUsages == 1) Action.INLINE else Action.MOVE
            else -> Action.NOTHING
        }
    }

    private fun KtProperty.countUsages(): Int {
        val identifier = nameIdentifier ?: return 0
        val name = identifier.text
        var count = 0

        val container = parent
        if (container is KtBlockExpression) {
            container.statements.asSequence()
                .dropWhile { it != this }
                .drop(1)
                .forEach { statement ->
                    statement.accept(object : KtTreeVisitorVoid() {
                        override fun visitReferenceExpression(expression: KtReferenceExpression) {
                            if (expression.text == name) count++
                            super.visitReferenceExpression(expression)
                        }
                    })
                }
        }

        return count
    }

    private fun KtProperty.countUsages(element: KtElement): Int {
        val identifier = nameIdentifier ?: return 0
        val name = identifier.text
        var count = 0

        element.accept(object : KtTreeVisitorVoid() {
            override fun visitReferenceExpression(expression: KtReferenceExpression) {
                if (expression.text == name) count++
                super.visitReferenceExpression(expression)
            }
        })

        return count
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

    private fun KtElement.previousStatement(): KtElement? {
        val container = parent as? KtBlockExpression ?: return null
        val statements = container.statements
        val index = statements.indexOf(this)
        if (index <= 0) return null
        return statements[index - 1]
    }

    private class VariableDeclarationIntoWhenFix(
        private val context: Context
    ) : KotlinModCommandQuickFix<KtProperty>() {
        override fun getFamilyName(): @IntentionFamilyName String = when (context.action) {
            Action.MOVE -> KotlinBundle.message("move.variable.declaration.into.when")
            Action.INLINE -> KotlinBundle.message("inline.variable")
            else -> error("Illegal action")
        }

        override fun applyFix(
          project: Project,
          element: KtProperty,
          updater: ModPsiUpdater,
        ) {
            val property = updater.getWritable(element) ?: return
            val subjectExpression = context.subjectExpression.dereference()?.let(updater::getWritable) ?: return
            val newElement = property.copy() as? KtProperty ?: return
            val toReplace = when (context.action) {
                Action.MOVE -> newElement
                Action.INLINE -> newElement.initializer
                else -> return
            } ?: return

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

            updater.moveCaretTo((resultElement as? KtProperty)?.nameIdentifier?.startOffset ?: resultElement.startOffset)
        }
    }
}