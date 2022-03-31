// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage


fun reportErrorAndAttachStorage(message: String, storage: WorkspaceEntityStorage) {
  error(message)
}

internal fun reportConsistencyIssue(message: String,
                                    e: Throwable,
                                    sourceFilter: ((EntitySource) -> Boolean)?,
                                    left: WorkspaceEntityStorage?,
                                    right: WorkspaceEntityStorage?,
                                    resulting: WorkspaceEntityStorage) {
    throw e
}

