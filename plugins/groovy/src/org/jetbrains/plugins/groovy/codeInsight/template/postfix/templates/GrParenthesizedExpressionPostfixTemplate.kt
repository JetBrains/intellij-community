// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.ParenthesizedPostfixTemplate
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.GroovyPostfixTemplateUtils

class GrParenthesizedExpressionPostfixTemplate :
  ParenthesizedPostfixTemplate(GroovyPostfixTemplateUtils.GROOVY_PSI_INFO, GroovyPostfixTemplateUtils.getExpressionSelector()) {

  override fun getId(): String = "groovy.postfix.template.par"
}