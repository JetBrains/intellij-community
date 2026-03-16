// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.template.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrAllPostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrArgPostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrCastExpressionPostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrElsePostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrFilterPostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrFlatMapPostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrFoldLeftPostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrForeachPostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrIfNotNullExpressionPostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrIfNullExpressionPostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrIfPostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrIntroduceVariablePostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrMapPostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrNegateBooleanPostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrNewExpressionPostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrParenthesizedExpressionPostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrReducePostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrReqnonnullExpressionPostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrReturnExpressionPostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrSerrExpressionPostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrSoutExpressionPostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrThrowExpressionPostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrTryPostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates.GrWhilePostfixTemplate

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
  GrArgPostfixTemplate(groovyPostfixTemplateProvider),
  GrParenthesizedExpressionPostfixTemplate(),
  GrCastExpressionPostfixTemplate(groovyPostfixTemplateProvider),
  GrForeachPostfixTemplate("for", groovyPostfixTemplateProvider),
  GrForeachPostfixTemplate("iter", groovyPostfixTemplateProvider),
  GrNewExpressionPostfixTemplate(groovyPostfixTemplateProvider),
  GrIfNotNullExpressionPostfixTemplate(groovyPostfixTemplateProvider, "nn"),
  GrIfNotNullExpressionPostfixTemplate(groovyPostfixTemplateProvider, "notnull"),
  GrIfNullExpressionPostfixTemplate(groovyPostfixTemplateProvider),
  GrIfPostfixTemplate(groovyPostfixTemplateProvider),
  GrElsePostfixTemplate(groovyPostfixTemplateProvider),
  GrReqnonnullExpressionPostfixTemplate(groovyPostfixTemplateProvider),
  GrReturnExpressionPostfixTemplate(groovyPostfixTemplateProvider),
  GrSoutExpressionPostfixTemplate(groovyPostfixTemplateProvider),
  GrSerrExpressionPostfixTemplate(groovyPostfixTemplateProvider),
  GrThrowExpressionPostfixTemplate(groovyPostfixTemplateProvider),
  GrTryPostfixTemplate(groovyPostfixTemplateProvider),
  GrMapPostfixTemplate(groovyPostfixTemplateProvider),
  GrIntroduceVariablePostfixTemplate("var", groovyPostfixTemplateProvider),
  GrIntroduceVariablePostfixTemplate("def", groovyPostfixTemplateProvider),
  GrWhilePostfixTemplate(groovyPostfixTemplateProvider),
  GrNegateBooleanPostfixTemplate("not", groovyPostfixTemplateProvider),
  GrAllPostfixTemplate(groovyPostfixTemplateProvider),
  GrFilterPostfixTemplate(groovyPostfixTemplateProvider),
  GrFlatMapPostfixTemplate(groovyPostfixTemplateProvider),
  GrFoldLeftPostfixTemplate(groovyPostfixTemplateProvider),
  GrReducePostfixTemplate(groovyPostfixTemplateProvider),
)