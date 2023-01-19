// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.java.JavaBundle
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.refactoring.introduce.introduceVariable.KotlinIntroduceVariableHandler
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtStatementExpression
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.types.typeUtil.isNothing
import org.jetbrains.kotlin.types.typeUtil.isUnit

class IntroduceVariableIntention : SelfTargetingIntention<PsiElement>(
    PsiElement::class.java, { JavaBundle.message("intention.introduce.variable.text") }
), HighPriorityAction {
    private fun getExpressionToProcess(element: PsiElement): KtExpression? {
        if (element is PsiFileSystemItem) return null
        val startElement = element.siblings(forward = false, withItself = true).firstOrNull { it !is PsiWhiteSpace } ?: element
        return startElement.parentsWithSelf
            .filterIsInstance<KtExpression>()
            .takeWhile { it !is KtDeclarationWithBody && it !is KtStatementExpression }
            .firstOrNull {
                val parent = it.parent
                parent is KtBlockExpression || parent is KtDeclarationWithBody && !parent.hasBlockBody() && parent.bodyExpression == it
            }
    }

    override fun isApplicableTo(element: PsiElement, caretOffset: Int): Boolean {
        val expression = getExpressionToProcess(element) ?: return false

        val editor = element.findExistingEditor()
        if (editor != null && editor.document.getLineNumber(caretOffset) > expression.getLineNumber(start = false)) return false

        val type = expression.safeAnalyzeNonSourceRootCode().getType(expression) ?: return false
        return !type.isUnit() && !type.isNothing()
    }

    override fun applyTo(element: PsiElement, editor: Editor?) {
        val expression = getExpressionToProcess(element) ?: return
        KotlinIntroduceVariableHandler.doRefactoring(
            element.project, editor, expression, isVar = false, occurrencesToReplace = null, onNonInteractiveFinish = null
        )
    }

    override fun startInWriteAction(): Boolean {
        return false
    }
}