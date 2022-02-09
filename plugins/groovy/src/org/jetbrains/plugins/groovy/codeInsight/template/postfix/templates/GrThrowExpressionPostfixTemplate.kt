// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiElement
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.GroovyPostfixTemplateUtils
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression

class GrThrowExpressionPostfixTemplate(provider: PostfixTemplateProvider) :
  GrPostfixTemplateBase("throw", "throw expr", GroovyPostfixTemplateUtils.getTopExpressionSelector(), provider) {

  override fun getGroovyTemplateString(element: PsiElement): String {
    element as GrExpression
    val type = element.type
    return if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_THROWABLE)) {
      "throw __expr____END__"
    } else {
      "throw new RuntimeException(__expr__)__END__"
    }
  }
}