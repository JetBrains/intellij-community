// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import org.jetbrains.annotations.ApiStatus

/**
 * Please don't implement this extension point.
 * At the moment it's created to support some concrete cases for workspace model.
 */
@ApiStatus.Internal
interface WorkspaceModelPreUpdateHandler {
  fun update(before: WorkspaceEntityStorage, builder: WorkspaceEntityStorageBuilder): Boolean
}
