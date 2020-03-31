// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.api

import com.intellij.lang.java.beans.PropertyKind
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.ResolveState
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.lValueProcessor
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.resolveKinds
import org.jetbrains.plugins.groovy.lang.psi.util.checkKind
import org.jetbrains.plugins.groovy.lang.psi.util.getPropertyNameAndKind
import org.jetbrains.plugins.groovy.lang.resolve.GrResolverProcessor
import org.jetbrains.plugins.groovy.lang.resolve.processReceiver

abstract class GroovyPropertyWriteReferenceBase<T : PsiElement>(element: T) : GroovyCachingReference<T>(element),
                                                                              GroovyPropertyWriteReference {

  override fun doResolve(incomplete: Boolean): Collection<GroovyResolveResult> {
    val place: T = element
    val receiver: Argument = receiverArgument ?: UnknownArgument
    val propertyName: String = propertyName
    val argument: Argument? = if (incomplete) null else argument
    val state: ResolveState = ResolveState.initial()
    val propertyProcessor: GrResolverProcessor<*> = lValueProcessor(propertyName, place, resolveKinds(true), argument)
    receiver.processReceiver(propertyProcessor, state, place)
    return propertyProcessor.results
  }

  override fun handleElementRename(newElementName: String): PsiElement {
    return super.handleElementRename(getPropertyName(newElementName) ?: newElementName)
  }

  private fun getPropertyName(newElementName: String): String? {
    val resolved: PsiMethod = resolve() as? PsiMethod ?: return null
    val referencedName: String = propertyName
    if (resolved.name == referencedName) {
      return null
    }
    if (!resolved.checkKind(PropertyKind.SETTER)) {
      return null
    }
    val (newPropertyName: String, propertyKind: PropertyKind) = getPropertyNameAndKind(newElementName) ?: return null
    if (propertyKind != PropertyKind.SETTER) {
      return null
    }
    return newPropertyName
  }
}
