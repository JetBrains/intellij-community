// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.imports.impl

import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.containers.ContainerUtil.createConcurrentSoftValueMap
import org.jetbrains.plugins.groovy.lang.psi.GroovyFileBase
import org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyImport

internal inline fun <I : GroovyImport, T> GroovyFileBase.resolve(import: I, crossinline resolver: I.() -> T): T {
  val cache = CachedValuesManager.getCachedValue(this) {
    Result.create(createConcurrentSoftValueMap<I, T>(), PsiModificationTracker.MODIFICATION_COUNT)
  }
  return cache.computeIfAbsent(import) {
    it.resolver()
  }
}
