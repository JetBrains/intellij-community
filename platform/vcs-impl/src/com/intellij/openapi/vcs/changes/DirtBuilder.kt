// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath

internal class DirtBuilder {
  private val scopesByVcs: MutableMap<AbstractVcs, VcsDirtyScopeImpl> = mutableMapOf()
  var isEverythingDirty: Boolean = false

  fun getScopes(): List<VcsDirtyScopeImpl> = scopesByVcs.values.toList()
  fun isEmpty(): Boolean = scopesByVcs.isEmpty()
  fun isFileDirty(filePath: FilePath): Boolean = isEverythingDirty || scopesByVcs.values.any { it.belongsTo(filePath) }
  fun getScope(vcs: AbstractVcs) = scopesByVcs.computeIfAbsent(vcs) { VcsDirtyScopeImpl(it) }
}