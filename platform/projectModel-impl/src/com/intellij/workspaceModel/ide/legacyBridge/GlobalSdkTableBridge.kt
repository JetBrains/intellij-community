// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange

interface GlobalSdkTableBridge {
  fun initializeSdkBridgesAfterLoading(mutableStorage: MutableEntityStorage,
                                                initialEntityStorage: VersionedEntityStorage): () -> Unit
  fun initializeSdkBridges(changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage)
  fun handleBeforeChangeEvents(event: VersionedStorageChange)
  fun handleChangedEvents(event: VersionedStorageChange)

  companion object {
    fun getInstance(): GlobalSdkTableBridge = ApplicationManager.getApplication().service()
    fun isEnabled(): Boolean = Registry.`is`("workspace.model.global.sdk.bridge", false)
  }
}