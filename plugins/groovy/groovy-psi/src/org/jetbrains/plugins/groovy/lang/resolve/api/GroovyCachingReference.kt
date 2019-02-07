// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.api

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPolyVariantReference
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyDependentReference
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.types.TypeInferenceHelper
import org.jetbrains.plugins.groovy.lang.resolve.DependentResolver
import org.jetbrains.plugins.groovy.lang.resolve.GroovyResolver

abstract class GroovyCachingReference<T : PsiElement>(element: T) : GroovyReferenceBase<T>(element) {

  final override fun resolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    val resolver = if (this is GroovyDependentReference) DefaultDependentResolver else DefaultResolver
    return TypeInferenceHelper.getCurrentContext().resolve(this, incomplete, resolver)
  }

  private object DefaultResolver : GroovyResolver<GroovyCachingReference<*>> {

    override fun resolve(ref: GroovyCachingReference<*>, incomplete: Boolean) = ref.doResolve(incomplete)
  }

  private object DefaultDependentResolver : DependentResolver<GroovyCachingReference<*>>() {

    override fun doResolve(ref: GroovyCachingReference<*>, incomplete: Boolean): Collection<GroovyResolveResult> {
      return ref.doResolve(incomplete)
    }

    override fun collectDependencies(ref: GroovyCachingReference<*>): Collection<PsiPolyVariantReference>? {
      return (ref as GroovyDependentReference).collectDependencies()
    }
  }

  abstract fun doResolve(incomplete: Boolean): Collection<GroovyResolveResult>
}
