// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq

import com.intellij.openapi.components.service
import com.intellij.psi.PsiType
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.GrDelegatesToProvider
import org.jetbrains.plugins.groovy.transformations.macro.GroovyMacroRegistryService

class GinqDelegatesToProvider : GrDelegatesToProvider {
  override fun getDelegatesToInfo(expression: GrFunctionalExpression): DelegatesToInfo? {
    val macroService = expression.project.service<GroovyMacroRegistryService>()
    val ginqSupport = GinqMacroTransformationSupport()
    if (expression.parentOfType<GrMethodCall>()?.takeIf { (it.closureArguments.toList() + it.expressionArguments).any { it == expression} }?.let(macroService::resolveAsMacro)?.let(ginqSupport::isApplicable) != true) {
      return null
    }
    val queryable = PsiType.getTypeByName(ORG_APACHE_GROOVY_GINQ_PROVIDER_COLLECTION_RUNTIME_QUERYABLE, expression.project, expression.resolveScope)
    return DelegatesToInfo(queryable)
  }
}