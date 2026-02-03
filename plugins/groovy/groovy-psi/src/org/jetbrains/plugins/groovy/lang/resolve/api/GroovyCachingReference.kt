// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

    override fun resolve(ref: GroovyCachingReference<*>, incomplete: Boolean): Array<GroovyResolveResult> = ref.doResolve(incomplete).toTypedArray()
  }

  private object DefaultDependentResolver : DependentResolver<GroovyCachingReference<*>>() {

    override fun doResolve(ref: GroovyCachingReference<*>, incomplete: Boolean): Array<GroovyResolveResult> {
      return ref.doResolve(incomplete).toTypedArray()
    }

    override fun collectDependencies(ref: GroovyCachingReference<*>): Collection<PsiPolyVariantReference>? {
      return (ref as GroovyDependentReference).collectDependencies()
    }
  }

  abstract fun doResolve(incomplete: Boolean): Collection<GroovyResolveResult>
}
