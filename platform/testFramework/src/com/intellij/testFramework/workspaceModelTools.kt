// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.storage.MutableEntityStorage

fun <R> WorkspaceModel.updateProjectModel(updater: (MutableEntityStorage) -> R): R {
  return updateProjectModel("Test update", updater)
}
