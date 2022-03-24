// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl.indices

import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData

interface WorkspaceMutableIndex<D> {
  fun index(entity: WorkspaceEntityData<*>, data: D)
  fun remove(entity: WorkspaceEntityData<*>, data: D)
}
