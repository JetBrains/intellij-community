// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.roots.impl.libraries.ApplicationLibraryTable
import com.intellij.workspaceModel.ide.JpsGlobalFileEntitySource
import com.intellij.workspaceModel.ide.getGlobalInstance
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.TestOnly

object JpsGlobalEntitiesSerializers {
  private var forceEnableLoading = false
  private val prohibited: Boolean
    get() = !forceEnableLoading && ApplicationManager.getApplication().isUnitTestMode

  fun createApplicationSerializers(): JpsFileEntitiesSerializer<LibraryEntity>? {
    if (prohibited) return null
    val virtualFileUrlManager = VirtualFileUrlManager.getGlobalInstance()
    val globalLibrariesFile = virtualFileUrlManager.fromUrl(PathManager.getOptionsFile(ApplicationLibraryTable.getExternalFileName()).absolutePath)
    val globalLibrariesEntitySource = JpsGlobalFileEntitySource(globalLibrariesFile)
    return JpsGlobalLibrariesFileSerializer(globalLibrariesEntitySource)
  }

  @TestOnly
  fun enableGlobalEntitiesLoading() {
    forceEnableLoading = true
  }

  @TestOnly
  fun disableGlobalEntitiesLoading() {
    forceEnableLoading = false
  }
}

interface ApplicationStoreJpsContentReader {
  fun createContentReader(): JpsFileContentReader
}