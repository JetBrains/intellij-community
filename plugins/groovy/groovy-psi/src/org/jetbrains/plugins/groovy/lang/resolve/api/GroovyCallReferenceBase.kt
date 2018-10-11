// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.api

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyReferenceBase
import org.jetbrains.plugins.groovy.lang.resolve.impl.resolveImpl

abstract class GroovyCallReferenceBase<T : PsiElement>(element: T) : GroovyReferenceBase<T>(element), GroovyCallReference {

  override fun doResolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    if (realReference) {
      return resolveImpl(incomplete)
    }
    else {
      return emptyList()
    }
  }
}
