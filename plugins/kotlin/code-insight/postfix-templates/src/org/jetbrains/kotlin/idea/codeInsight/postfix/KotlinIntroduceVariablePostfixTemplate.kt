// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateWithExpressionSelector
import com.intellij.lang.LanguageRefactoringSupport
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.idea.refactoring.KotlinCommonRefactoringSettings
import org.jetbrains.kotlin.idea.refactoring.introduce.KotlinIntroduceVariableHandler
import org.jetbrains.kotlin.psi.KtExpression

internal abstract class KotlinIntroduceVariablePostfixTemplate(
    val kind: String,
    provider: PostfixTemplateProvider
) : PostfixTemplateWithExpressionSelector(
    /* id = */ kind,
    /* name = */ kind,
    /* example = */ "$kind name = expression",
    /* selector = */ allExpressions(ValuedFilter, NonPackageAndNonImportFilter),
    provider
) {
    @OptIn(KaAllowAnalysisOnEdt::class)
    override fun expandForChooseExpression(expression: PsiElement, editor: Editor) {
        val isVar = kind == "var"
        val provider = LanguageRefactoringSupport.getInstance().forLanguage(KotlinLanguage.INSTANCE)
        val introduceVariableHandler = provider.introduceVariableHandler as KotlinIntroduceVariableHandler
        allowAnalysisOnEdt {
            @OptIn(KaAllowAnalysisFromWriteAction::class)
            allowAnalysisFromWriteAction {
                introduceVariableHandler.collectCandidateTargetContainersAndDoRefactoring(
                    project = expression.project,
                    editor = editor,
                    expressionToExtract = expression as KtExpression,
                    isVar = isVar
                )
            }
            KotlinCommonRefactoringSettings.getInstance().INTRODUCE_DECLARE_WITH_VAR = isVar
        }
    }
}
