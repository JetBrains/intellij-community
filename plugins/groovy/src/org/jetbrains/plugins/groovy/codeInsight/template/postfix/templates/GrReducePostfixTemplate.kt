// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInsight.template.postfix.templates

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.GroovyPostfixTemplateProvider
import org.jetbrains.plugins.groovy.codeInsight.template.postfix.GroovyPostfixTemplateUtils

class GrReducePostfixTemplate(provider: GroovyPostfixTemplateProvider) :
  GrPostfixTemplateBase("reduce", "expr.inject {}", GroovyPostfixTemplateUtils.getIterableExpressionSelector(), provider) {

  override fun getGroovyTemplateString(element: PsiElement): String = "__expr__.inject {__END__}"
}