// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.workspaceModel

import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.storage.MutableEntityStorage

fun WorkspaceModel.updateProjectModel(updater: (MutableEntityStorage) -> Unit) {
  updateProjectModel("Test update", updater)
}

suspend fun WorkspaceModel.updateProjectModelAsync(updater: (MutableEntityStorage) -> Unit) {
  updateProjectModelAsync("Test update", updater)
}
