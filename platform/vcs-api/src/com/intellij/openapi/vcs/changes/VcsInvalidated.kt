// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.vcs.FilePath

class VcsInvalidated(val scopes: List<VcsDirtyScope>, val isEverythingDirty: Boolean) {
  fun isEmpty(): Boolean = scopes.isEmpty()

  fun isFileDirty(path: FilePath): Boolean = isEverythingDirty || scopes.any { it.belongsTo(path) }
}