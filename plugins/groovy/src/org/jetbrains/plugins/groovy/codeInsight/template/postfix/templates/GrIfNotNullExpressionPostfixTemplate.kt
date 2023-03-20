// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.GroovyPostfixTemplateUtils

class GrIfNotNullExpressionPostfixTemplate(provider: PostfixTemplateProvider, name : String) :
  GrPostfixTemplateBase(name, "if (expr != null)", GroovyPostfixTemplateUtils.getNullableTopExpressionSelector(), provider) {

  override fun getGroovyTemplateString(element: PsiElement): String =
"""if (__expr__ != null) {
    __END__
}"""
}