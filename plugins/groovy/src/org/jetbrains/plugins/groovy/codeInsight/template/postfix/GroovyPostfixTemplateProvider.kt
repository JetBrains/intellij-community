// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.template.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.*

class GroovyPostfixTemplateProvider : PostfixTemplateProvider {

  override fun getTemplates(): Set<PostfixTemplate> = getBuiltinTemplates(this)

  override fun isTerminalSymbol(currentChar: Char): Boolean = currentChar == '.' || currentChar == '|'

  override fun preExpand(file: PsiFile, editor: Editor) {
  }

  override fun afterExpand(file: PsiFile, editor: Editor) {
  }

  override fun preCheck(copyFile: PsiFile, realEditor: Editor, currentOffset: Int): PsiFile {
    return copyFile
  }

  override fun getId(): String = "builtin.groovy"

  override fun getPresentableName(): String = GroovyBundle.message("postfix.template.provider.name")

}

private fun getBuiltinTemplates(groovyPostfixTemplateProvider: GroovyPostfixTemplateProvider): Set<PostfixTemplate> = setOf(
  GrParenthesizedExpressionPostfixTemplate(),
  GrCastExpressionPostfixTemplate(groovyPostfixTemplateProvider),
  GrForeachPostfixTemplate(groovyPostfixTemplateProvider),
  GrNewExpressionPostfixTemplate(groovyPostfixTemplateProvider),
  GrIfNotNullExpressionPostfixTemplate(groovyPostfixTemplateProvider, "nn"),
  GrIfNotNullExpressionPostfixTemplate(groovyPostfixTemplateProvider, "notnull"),
  GrIfNullExpressionPostfixTemplate(groovyPostfixTemplateProvider),
  GrReqnonnullExpressionPostfixTemplate(groovyPostfixTemplateProvider),
  GrReturnExpressionPostfixTemplate(groovyPostfixTemplateProvider),
  GrSoutExpressionPostfixTemplate(groovyPostfixTemplateProvider),
  GrSerrExpressionPostfixTemplate(groovyPostfixTemplateProvider),
)