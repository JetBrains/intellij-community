// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.workspace.storage.impl.indices

import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData

public interface WorkspaceMutableIndex<D> {
  public fun index(entity: WorkspaceEntityData<*>, data: D)
  public fun remove(entity: WorkspaceEntityData<*>, data: D)
}
