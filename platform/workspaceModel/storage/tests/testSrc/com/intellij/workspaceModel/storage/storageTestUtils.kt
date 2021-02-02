// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.workspaceModel.storage

import com.intellij.workspaceModel.storage.impl.ConsistencyCheckingMode
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageBuilderImpl
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityStorageImpl

fun WorkspaceEntityStorage.checkConsistency() {
  if (this is WorkspaceEntityStorageImpl) {
    this.assertConsistency()
    return
  }

  if (this is WorkspaceEntityStorageBuilderImpl) {
    this.assertConsistency()
    return
  }
}

internal fun createEmptyBuilder(): WorkspaceEntityStorageBuilderImpl {
  return WorkspaceEntityStorageBuilderImpl.create(ConsistencyCheckingMode.SYNCHRONOUS)
}

internal fun createBuilderFrom(storage: WorkspaceEntityStorage): WorkspaceEntityStorageBuilderImpl {
  return WorkspaceEntityStorageBuilderImpl.from(storage, ConsistencyCheckingMode.SYNCHRONOUS)
}