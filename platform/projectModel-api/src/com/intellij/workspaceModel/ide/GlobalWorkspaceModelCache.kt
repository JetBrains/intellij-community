// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.workspaceModel.storage.EntityStorage
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface GlobalWorkspaceModelCache {
  fun loadCache(): EntityStorage?
  fun scheduleCacheSave()

  companion object {
    fun getInstance(): GlobalWorkspaceModelCache? =
      ApplicationManager.getApplication().getService(GlobalWorkspaceModelCache::class.java)
  }
}