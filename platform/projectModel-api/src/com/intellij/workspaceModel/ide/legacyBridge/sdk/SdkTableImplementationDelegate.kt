// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.legacyBridge.sdk

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
interface SdkTableImplementationDelegate {
  fun addNewSdk(sdk: Sdk)
  fun removeSdk(sdk: Sdk)
  fun updateSdk(originalSdk: Sdk, modifiedSdk: Sdk)

  fun createSdk(name: String, type: SdkTypeId, homePath: String?): Sdk

  fun getAllSdks(): List<Sdk>

  fun findSdkByName(name: String): Sdk?

  @TestOnly
  fun saveOnDisk()

  companion object {
    fun getInstance(): SdkTableImplementationDelegate {
      return ApplicationManager.getApplication().service()
      //return ExtensionPointName.create<SdkTableImplementationDelegate>("com.intellij.workspaceModel.sdk.implementation.delegate").extensions.first()
    }
  }
}