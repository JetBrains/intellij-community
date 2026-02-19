// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.ast.contributor

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.ElementClassHint.DeclarationKind
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames

class PrePostParametersContributor : AbstractGeneratedConstructorContributor() {
  override fun failFastCheck(processor: PsiScopeProcessor, state: ResolveState): Boolean {
    return processor.getHint(ElementClassHint.KEY)?.shouldProcess(DeclarationKind.VARIABLE) != true
  }

  override fun generateSyntheticElements(annotation: PsiAnnotation, closure: GrClosableBlock, mode: String): Iterable<PsiElement> {
    if (annotation.hasQualifiedName(GroovyCommonClassNames.GROOVY_TRANSFORM_MAP_CONSTRUCTOR)) {
      return getMapConstructorParameters(closure, annotation)
    } else {
      return emptyList()
    }
  }

  private fun getMapConstructorParameters(context: PsiElement, annotation: PsiAnnotation): Iterable<PsiElement> {
    return listOf(GrLightVariable(context.manager, "args", "java.util.Map<java.lang.String, ?>", annotation))
  }
}