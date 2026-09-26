// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.workspace.storage.InternalEnvironmentName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
interface SdkTableImplementationDelegate {
  fun addNewSdk(sdk: Sdk)
  fun removeSdk(sdk: Sdk)
  fun updateSdk(originalSdk: Sdk, modifiedSdk: Sdk)

  fun createSdk(name: String, type: SdkTypeId, homePath: String?): Sdk
  fun createSdk(name: String, type: SdkTypeId, environmentName: InternalEnvironmentName): Sdk

  fun getAllSdks(): List<Sdk>

  fun findSdkByName(name: String): Sdk?
  fun findSdkByName(name: String, eelMachine: EelMachine): Sdk?

  @TestOnly
  fun saveOnDisk()

  companion object {
    @Suppress("IncorrectServiceRetrieving") // registered programmatically
    fun getInstance(): SdkTableImplementationDelegate {
      return ApplicationManager.getApplication().service()
    }
  }
}