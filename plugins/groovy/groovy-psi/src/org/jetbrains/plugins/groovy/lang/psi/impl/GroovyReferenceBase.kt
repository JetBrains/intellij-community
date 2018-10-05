// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReferenceBase
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolver

abstract class GroovyReferenceBase<T : PsiElement>(element: T) : PsiPolyVariantReferenceBase<T>(element), GroovyReference {

  final override fun resolve(): PsiElement? = super<GroovyReference>.resolve()

  final override fun resolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    return TypeInferenceHelper.getCurrentContext().resolve(this, incomplete, Resolver)
  }

  private object Resolver : GroovyResolver<GroovyReferenceBase<*>> {
    override fun resolve(ref: GroovyReferenceBase<*>, incomplete: Boolean) = ref.doResolve(incomplete)
  }

  abstract fun doResolve(incomplete: Boolean): Collection<GroovyResolveResult>
}
