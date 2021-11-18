// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types

import com.intellij.psi.PsiType
import com.intellij.util.castSafelyTo
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ResolvedVariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.util.isCompileStatic


internal class InitialTypeProvider(private val start: GrControlFlowOwner) {
  private val TYPE_INFERENCE_FAILED = Any()
  private val cache: MutableMap<VariableDescriptor, Any> = mutableMapOf()

  fun initialType(descriptor: VariableDescriptor): PsiType? {
    if (!cache.containsKey(descriptor)) {
      try {
        if (isCompileStatic(start)) return null
        val resolvedDescriptor = descriptor as? ResolvedVariableDescriptor ?: return null
        val field = resolvedDescriptor.variable as? GrField ?: return null
        val fieldType = field.typeGroovy
        if (fieldType != null) {
          cache[descriptor] = fieldType
        }
      } finally {
        cache.putIfAbsent(descriptor, TYPE_INFERENCE_FAILED)
      }
    }
    return cache[descriptor]?.castSafelyTo<PsiType>()
  }
}
