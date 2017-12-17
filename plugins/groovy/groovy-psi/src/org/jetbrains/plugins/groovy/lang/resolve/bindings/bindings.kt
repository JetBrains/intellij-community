/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.resolve.bindings

import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable
import java.util.concurrent.ConcurrentMap

internal fun GroovyFile.getBindings(): ConcurrentMap<String, GrVariable> {
  return CachedValuesManager.getCachedValue(this) {
    Result.create(
      ContainerUtil.newConcurrentMap<String, GrVariable>(),
      PsiModificationTracker.MODIFICATION_COUNT
    )
  }
}

internal fun GroovyFile.getBinding(name: String): GrVariable {
  val bindings = getBindings()
  bindings[name]?.let { return it }
  return ConcurrencyUtil.cacheOrGet(bindings, name, GrBindingVariable(this, name))
}
