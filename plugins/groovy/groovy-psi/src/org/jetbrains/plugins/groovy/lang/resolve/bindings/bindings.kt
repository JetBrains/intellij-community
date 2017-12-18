/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.resolve.bindings

import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTupleAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable
import org.jetbrains.plugins.groovy.lang.resolve.getName
import org.jetbrains.plugins.groovy.lang.resolve.processStatements
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessDynamicProperties
import java.util.concurrent.ConcurrentMap

private fun GroovyFile.getBindings(): ConcurrentMap<String, GrVariable> {
  return CachedValuesManager.getCachedValue(this) {
    Result.create(
      ContainerUtil.newConcurrentMap<String, GrVariable>(),
      PsiModificationTracker.MODIFICATION_COUNT
    )
  }
}

private fun GroovyFile.getBinding(name: String): GrVariable {
  val bindings = getBindings()
  bindings[name]?.let { return it }
  return ConcurrencyUtil.cacheOrGet(bindings, name, GrBindingVariable(this, name))
}

internal fun GroovyFile.processBindings(processor: PsiScopeProcessor,
                                        state: ResolveState,
                                        lastParent: PsiElement?,
                                        place: PsiElement): Boolean {
  if (!processor.shouldProcessDynamicProperties()) return true

  if (lastParent is GrTypeDefinition) return true
  if (!isScript) return true

  val hintName = processor.getName(state)

  fun GrExpression.processLValue(): Boolean {
    if (this !is GrReferenceExpression) return true
    if (isQualified) return true

    val name = referenceName ?: return true
    if (hintName != null && name != hintName) return true

    val resolved = if (place === this) null else resolve()
    val binding = when (resolved) {
      null -> getBinding(name)
      is GrBindingVariable -> resolved
      else -> return true
    }
    return processor.execute(binding, state)
  }

  return processStatements(lastParent?.nextSibling) {
    when (it) {
      is GrAssignmentExpression -> it.lValue.processLValue()
      is GrTupleAssignmentExpression -> it.lValue.expressions.all { it.processLValue() }
      else -> true
    }
  }
}
