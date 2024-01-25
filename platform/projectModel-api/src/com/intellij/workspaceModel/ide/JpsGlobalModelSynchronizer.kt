// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface JpsGlobalModelSynchronizer {
  fun loadInitialState(mutableStorage: MutableEntityStorage,
                       initialEntityStorage: VersionedEntityStorage,
                       loadedFromCache: Boolean): () -> Unit
  fun setVirtualFileUrlManager(vfuManager: VirtualFileUrlManager)

  companion object {
    fun getInstance(): JpsGlobalModelSynchronizer = ApplicationManager.getApplication().service()
  }
}