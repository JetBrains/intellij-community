// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.api

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.impl.resolveImpl

abstract class GroovyMethodCallReferenceBase<T : PsiElement>(element: T) : GroovyCachingReference<T>(element), GroovyMethodCallReference {

  override fun doResolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    if (isRealReference) {
      return resolveImpl(incomplete)
    }
    else {
      return fakeResolve(incomplete)
    }
  }

  protected open fun fakeResolve(incomplete: Boolean): Collection<GroovyResolveResult> = emptyList()
}
