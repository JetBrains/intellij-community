// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.template.expressions

import com.intellij.codeInsight.template.ExpressionContext
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.psi.codeStyle.VariableKind
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter

class StringParameterNameExpression(private val myDefaultName: String?) : ParameterNameExpression() {

  override fun getNameInfo(context: ExpressionContext): SuggestedNameInfo? {
    val project = context.project
    val editor = context.editor ?: return null
    val file = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null
    val elementAt = file.findElementAt(context.startOffset)
    val parameter = PsiTreeUtil.getParentOfType(elementAt, GrParameter::class.java) ?: return null
    val manager = JavaCodeStyleManager.getInstance(project)
    return manager.suggestVariableName(VariableKind.PARAMETER, myDefaultName, null, parameter.typeGroovy)
  }

  companion object {
    val EMPTY: ParameterNameExpression = StringParameterNameExpression(null)
  }
}
