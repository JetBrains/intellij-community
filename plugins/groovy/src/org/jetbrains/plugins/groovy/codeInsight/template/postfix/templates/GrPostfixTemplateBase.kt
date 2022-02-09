// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelector
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement

abstract class GrPostfixTemplateBase(
  sequence: String,
  example: String,
  selector: PostfixTemplateExpressionSelector,
  provider: PostfixTemplateProvider) : StringBasedPostfixTemplate(sequence, example, selector, provider) {

  final override fun getId(): String = "groovy.postfix.template.$presentableName"

  final override fun getTemplateString(element: PsiElement): String = getGroovyTemplateString(element).replace("__", "$")

  abstract fun getGroovyTemplateString(element: PsiElement): String

  override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}