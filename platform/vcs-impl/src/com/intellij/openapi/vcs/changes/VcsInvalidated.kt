// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.vcs.FilePath
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class VcsInvalidated(val scopes: List<VcsModifiableDirtyScope>, val isEverythingDirty: Boolean, val callback: ActionCallback) {
  fun isEmpty(): Boolean = scopes.isEmpty()

  fun isFileDirty(path: FilePath): Boolean = isEverythingDirty || scopes.any { it.belongsTo(path) }

  fun doWhenCanceled(task: Runnable) {
    callback.doWhenRejected(task)
  }
}