// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.GroovyPostfixTemplateUtils
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression

class GrTryPostfixTemplate(provider: PostfixTemplateProvider) :
  GrPostfixTemplateBase("try", "try { expr } catch(e) { ... }", GroovyPostfixTemplateUtils.getTopExpressionSelector(), provider) {

  override fun getGroovyTemplateString(element: PsiElement): String {
    val exceptionList = if (element is GrMethodCallExpression) {
      val method = element.resolveMethod()
      method?.throwsList?.referencedTypes?.mapNotNull { it?.resolve()?.qualifiedName } ?: listOf(CommonClassNames.JAVA_LANG_EXCEPTION)
    } else {
      listOf(CommonClassNames.JAVA_LANG_EXCEPTION)
    }
    val list = exceptionList.joinToString(" | ")
    return """try {
    __expr__
} catch ($list e) {
    __END__
}"""
  }
}