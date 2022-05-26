// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow.types

import com.intellij.psi.PsiType
import com.intellij.util.castSafelyTo
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.VariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ResolvedVariableDescriptor
import org.jetbrains.plugins.groovy.lang.psi.util.isCompileStatic


internal class InitialTypeProvider(private val start: GrControlFlowOwner, private val reverseMapping : Array<VariableDescriptor>) {
  private val TYPE_INFERENCE_FAILED = Any()
  private val cache: MutableMap<Int, Any> = mutableMapOf()

  fun initialType(descriptorId: Int): PsiType? {
    if (!cache.containsKey(descriptorId)) {
      try {
        if (isCompileStatic(start)) return null
        val field = reverseMapping[descriptorId].castSafelyTo<ResolvedVariableDescriptor>()?.variable?.castSafelyTo<GrField>() ?: return null
        val fieldType = field.typeGroovy
        if (fieldType != null) {
          cache[descriptorId] = fieldType
        }
      } finally {
        cache.putIfAbsent(descriptorId, TYPE_INFERENCE_FAILED)
      }
    }
    return cache[descriptorId]?.castSafelyTo<PsiType>()
  }
}
