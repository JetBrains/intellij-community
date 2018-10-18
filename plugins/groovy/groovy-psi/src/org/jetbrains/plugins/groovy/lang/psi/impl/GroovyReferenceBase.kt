// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiPolyVariantReferenceBase
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyDependentReference
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper
import org.jetbrains.plugins.groovy.lang.resolve.DependentResolver
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolver

abstract class GroovyReferenceBase<T : PsiElement>(element: T) : PsiPolyVariantReferenceBase<T>(element), GroovyReference {

  final override fun resolve(): PsiElement? = super<GroovyReference>.resolve()

  final override fun resolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    val resolver = if (this is GroovyDependentReference) DefaultDependentResolver else DefaultResolver
    return TypeInferenceHelper.getCurrentContext().resolve(this, incomplete, resolver)
  }

  private object DefaultResolver : GroovyResolver<GroovyReferenceBase<*>> {

    override fun resolve(ref: GroovyReferenceBase<*>, incomplete: Boolean) = ref.doResolve(incomplete)
  }

  private object DefaultDependentResolver : DependentResolver<GroovyReferenceBase<*>>() {

    override fun doResolve(ref: GroovyReferenceBase<*>, incomplete: Boolean): Collection<GroovyResolveResult> {
      return ref.doResolve(incomplete)
    }

    override fun collectDependencies(ref: GroovyReferenceBase<*>): Collection<PsiPolyVariantReference>? {
      return (ref as GroovyDependentReference).collectDependencies()
    }
  }

  abstract fun doResolve(incomplete: Boolean): Collection<GroovyResolveResult>
}
