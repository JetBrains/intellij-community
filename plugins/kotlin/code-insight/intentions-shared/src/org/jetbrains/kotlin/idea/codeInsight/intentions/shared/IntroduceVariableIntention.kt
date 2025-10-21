// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.java.JavaBundle
import com.intellij.lang.LanguageRefactoringSupport
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.findExistingEditor
import org.jetbrains.kotlin.idea.refactoring.introduce.KotlinIntroduceVariableHandler
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtScriptInitializer
import org.jetbrains.kotlin.psi.KtStatementExpression
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.psi.psiUtil.siblings

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
                parent is KtBlockExpression || parent is KtScriptInitializer || parent is KtDeclarationWithBody && !parent.hasBlockBody() && parent.bodyExpression == it
            }
    }

    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun isApplicableTo(element: PsiElement, caretOffset: Int): Boolean {
        val expression = getExpressionToProcess(element) ?: return false

        val editor = element.findExistingEditor()
        if (editor != null && editor.document.getLineNumber(caretOffset) > expression.getLineNumber(start = false)) return false

        return allowAnalysisOnEdt {
            analyze(expression) {
                expression.expressionType?.takeUnless { it.isUnitType } != null
            }
        }
    }

    override fun applyTo(element: PsiElement, editor: Editor?) {
        val expression = getExpressionToProcess(element) ?: return
        val introduceVariableHandler =
            LanguageRefactoringSupport.getInstance().forLanguage(KotlinLanguage.INSTANCE).introduceVariableHandler as KotlinIntroduceVariableHandler
        introduceVariableHandler.collectCandidateTargetContainersAndDoRefactoring(
            element.project, editor, expression, isVar = false, occurrencesToReplace = null, onNonInteractiveFinish = null
        )
    }

    override fun startInWriteAction(): Boolean {
        return false
    }
}