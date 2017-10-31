// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.util.containers.ContainerUtil.toArray
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult

interface GrResolverProcessor<out T : GroovyResolveResult> : PsiScopeProcessor {

  val results: List<T>

  val resultsArray: Array<GroovyResolveResult> get() = toArray(results, GroovyResolveResult.EMPTY_ARRAY)
}
