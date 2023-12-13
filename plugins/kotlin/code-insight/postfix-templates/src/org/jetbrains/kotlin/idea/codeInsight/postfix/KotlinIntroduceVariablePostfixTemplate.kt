// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateWithExpressionSelector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable.K2IntroduceVariableHandler
import org.jetbrains.kotlin.psi.KtExpression

internal class KotlinIntroduceVariablePostfixTemplate(
    val kind: String,
    provider: PostfixTemplateProvider
) : PostfixTemplateWithExpressionSelector(
    /* id = */ kind,
    /* name = */ kind,
    /* example = */ "$kind name = expression",
    /* selector = */ allExpressions(ValuedFilter, NonPackageAndNonImportFilter),
    provider
) {
    @OptIn(KtAllowAnalysisOnEdt::class)
    override fun expandForChooseExpression(expression: PsiElement, editor: Editor) {
        allowAnalysisOnEdt {
            @OptIn(KtAllowAnalysisFromWriteAction::class)
            allowAnalysisFromWriteAction {
                K2IntroduceVariableHandler.collectCandidateTargetContainersAndDoRefactoring(
                    expression.project, editor, expression as KtExpression,
                    isVar = kind == "var"
                )
            }
        }
    }
}
