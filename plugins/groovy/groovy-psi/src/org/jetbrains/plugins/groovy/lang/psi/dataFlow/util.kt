// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.dataFlow

import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import gnu.trove.TObjectIntHashMap
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction

internal fun GrControlFlowOwner.getVarIndexes(): TObjectIntHashMap<String> {
  return CachedValuesManager.getCachedValue(this) {
    Result.create(doGetVarIndexes(this), PsiModificationTracker.MODIFICATION_COUNT)
  }
}

private fun doGetVarIndexes(owner: GrControlFlowOwner): TObjectIntHashMap<String> {
  val result = TObjectIntHashMap<String>()
  var num = 1
  for (instruction in owner.controlFlow) {
    if (instruction !is ReadWriteVariableInstruction) continue
    val name = instruction.variableName
    if (!result.containsKey(name)) {
      result.put(name, num++)
    }
  }
  return result
}
