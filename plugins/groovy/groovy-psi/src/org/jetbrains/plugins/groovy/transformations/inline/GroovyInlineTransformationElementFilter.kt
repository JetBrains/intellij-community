// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.inline

import org.jetbrains.plugins.groovy.lang.GroovyElementFilter
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement

class GroovyInlineTransformationElementFilter : GroovyElementFilter {
  override fun isFake(element: GroovyPsiElement): Boolean {
    val performer = getHierarchicalInlineTransformationPerformer(element) ?: return false
    return !performer.isUntransformed(element)
  }
}