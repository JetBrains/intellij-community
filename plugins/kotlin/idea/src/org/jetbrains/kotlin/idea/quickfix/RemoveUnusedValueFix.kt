// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.cfg.pseudocode.getContainingPseudocode
import org.jetbrains.kotlin.cfg.pseudocode.sideEffectFree
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class RemoveUnusedValueFix(expression: KtBinaryExpression) : KotlinQuickFixAction<KtBinaryExpression>(expression) {
    enum class RemoveMode {
        REMOVE_ALL, KEEP_INITIALIZE, CANCEL
    }

    private fun showDialog(variable: KtProperty, project: Project, element: KtBinaryExpression, rhs: KtExpression) {
        if (isUnitTestMode()) return doRemove(RemoveMode.KEEP_INITIALIZE, element, rhs)

        val message = "<html><body>${KotlinBundle.message(
            "there.are.possible.side.effects.found.in.expressions.assigned.to.the.variable.0",
            variable.name.toString()
        )}</body></html>"

        ApplicationManager.getApplication().invokeLater {
            val exitCode = Messages.showYesNoCancelDialog(
                variable.project,
                message,
                QuickFixBundle.message("side.effects.warning.dialog.title"),
                QuickFixBundle.message("side.effect.action.remove"),
                QuickFixBundle.message("side.effect.action.transform"),
                QuickFixBundle.message("side.effect.action.cancel"),
                Messages.getWarningIcon()
            )
            WriteCommandAction.runWriteCommandAction(project) {
                doRemove(RemoveMode.values()[exitCode], element, rhs)
            }
        }
    }

    override fun getFamilyName(): String = KotlinBundle.message("remove.redundant.assignment")

    override fun getText(): String = familyName

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        IntentionPreviewInfo.EMPTY

    private fun doRemove(mode: RemoveMode, element: KtBinaryExpression, rhs: KtExpression) {
        when (mode) {
            RemoveMode.REMOVE_ALL -> element.delete()
            RemoveMode.KEEP_INITIALIZE -> element.replace(rhs)
            else -> {
            }
        }
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val element = element ?: return
        val lhs = element.left as? KtSimpleNameExpression ?: return
        val rhs = element.right ?: return
        val variable = lhs.mainReference.resolve() as? KtProperty ?: return
        val pseudocode = rhs.getContainingPseudocode(element.analyze(BodyResolveMode.PARTIAL))
        val isSideEffectFree = pseudocode?.getElementValue(rhs)?.createdAt?.sideEffectFree ?: false
        if (!isSideEffectFree) {
            showDialog(variable, project, element, rhs)
        } else {
            doRemove(RemoveMode.REMOVE_ALL, element, rhs)
        }
    }

    companion object : KotlinSingleIntentionActionFactory() {
        override fun createAction(diagnostic: Diagnostic): IntentionAction? {
            val expression = Errors.UNUSED_VALUE.cast(diagnostic).psiElement
            if (!KtPsiUtil.isAssignment(expression)) return null
            if (expression.left !is KtSimpleNameExpression) return null
            return RemoveUnusedValueFix(expression)
        }
    }
}