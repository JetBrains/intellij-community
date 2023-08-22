// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq

import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod
import org.jetbrains.plugins.groovy.transformations.inline.GroovyInlineASTTransformationPerformer
import org.jetbrains.plugins.groovy.transformations.inline.GroovyInlineASTTransformationSupport

internal class GinqAnnotationTransformationSupport : GroovyInlineASTTransformationSupport {

  override fun getPerformer(transformationRoot: GroovyPsiElement): GroovyInlineASTTransformationPerformer? {
    if (transformationRoot !is GrMethod) {
      return null
    }
    return if (transformationRoot.hasAnnotation(GROOVY_GINQ_TRANSFORM_GQ)) GinqTransformationPerformer(GinqRootPsiElement.Method(transformationRoot)) else null
  }
}