// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.lang.java.beans.PropertyKind
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.util.isPropertyName
import org.jetbrains.plugins.groovy.lang.resolve.GrResolverProcessor

class GroovyLValueProcessor(
  name: String,
  place: PsiElement,
  kinds: Set<GroovyResolveKind>,
  argumentTypes: () -> Array<PsiType?>?
) : AccessorAwareResolverProcessor(name, place, kinds) {

  override val accessorProcessors: Collection<GrResolverProcessor<*>> = if (name.isPropertyName()) {
    listOf(AccessorProcessor(name, PropertyKind.SETTER, argumentTypes, place))
  }
  else {
    emptyList()
  }
}
