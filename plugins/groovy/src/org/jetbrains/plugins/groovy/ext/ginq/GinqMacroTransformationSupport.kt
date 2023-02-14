// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.transformations.inline.GroovyInlineASTTransformationPerformer
import org.jetbrains.plugins.groovy.transformations.inline.GroovyInlineASTTransformationSupport
import org.jetbrains.plugins.groovy.transformations.macro.GroovyMacroRegistryService

internal class GinqMacroTransformationSupport : GroovyInlineASTTransformationSupport {

  override fun getPerformer(transformationRoot: GroovyPsiElement): GroovyInlineASTTransformationPerformer? {
    if (DumbService.isDumb(transformationRoot.project)) {
      return null
    }
    if (transformationRoot !is GrMethodCall) {
      return null
    }
    val macroMethod = transformationRoot.project.service<GroovyMacroRegistryService>().resolveAsMacro(transformationRoot) ?: return null
    if (macroMethod.name !in GINQ_METHODS || macroMethod.containingClass?.name != "GinqGroovyMethods") return null
    return GinqTransformationPerformer(GinqRootPsiElement.Call(transformationRoot))
  }
}