// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.api

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.impl.resolveWithArguments

abstract class GroovyMethodCallReferenceBase<T : PsiElement>(element: T) : GroovyCachingReference<T>(element), GroovyMethodCallReference {

  override fun doResolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    val place = element
    val receiver = receiverArgument ?: UnknownArgument
    val methodName = methodName
    val arguments = if (incomplete) null else arguments
    return resolveWithArguments(receiver, methodName, arguments, place)
  }
}
