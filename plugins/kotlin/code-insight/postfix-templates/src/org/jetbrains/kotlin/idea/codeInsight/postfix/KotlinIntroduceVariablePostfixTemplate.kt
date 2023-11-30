// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateWithExpressionSelector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable.KotlinIntroduceVariableHandler
import org.jetbrains.kotlin.psi.KtExpression

internal class KotlinIntroduceVariablePostfixTemplate(
  val kind: String,
  provider: PostfixTemplateProvider
) : PostfixTemplateWithExpressionSelector(kind, kind, "$kind name = expression", allExpressions(NonPackageAndNonImportFilter), provider) {
    override fun expandForChooseExpression(expression: PsiElement, editor: Editor) {
        KotlinIntroduceVariableHandler.doRefactoring(
          expression.project, editor, expression as KtExpression,
          isVar = kind == "var"
        )
    }
}
