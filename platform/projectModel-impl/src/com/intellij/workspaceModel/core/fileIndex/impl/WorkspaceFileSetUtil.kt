// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.platform.workspace.storage.EntityPointer
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Deprecated("WorkspaceFileSet should not leak EntityPointer.")
fun WorkspaceFileSet.getEntityPointer(): EntityPointer<WorkspaceEntity>? {
  return (this as? WorkspaceFileSetImpl)?.entityPointer
}
