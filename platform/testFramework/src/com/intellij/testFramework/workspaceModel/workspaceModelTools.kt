// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.workspaceModel

import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.MutableEntityStorage

fun WorkspaceModel.updateProjectModel(updater: (MutableEntityStorage) -> Unit) {
  updateProjectModel("Test update", updater)
}

suspend fun WorkspaceModel.update(updater: (MutableEntityStorage) -> Unit) {
  update("Test update", updater)
}
