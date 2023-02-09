// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.platform.workspaceModel.jps.JpsGlobalFileEntitySource
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import org.jetbrains.annotations.TestOnly

object JpsGlobalEntitiesSerializers {
  const val GLOBAL_LIBRARIES_FILE_NAME: String = "applicationLibraries"
  private var forceEnableLoading = false
  private val prohibited: Boolean
    get() = !forceEnableLoading && ApplicationManager.getApplication().isUnitTestMode

  fun createApplicationSerializers(virtualFileUrlManager: VirtualFileUrlManager): JpsFileEntitiesSerializer<LibraryEntity>? {
    if (prohibited) return null
    val globalLibrariesFile = virtualFileUrlManager.fromUrl(PathManager.getOptionsFile(GLOBAL_LIBRARIES_FILE_NAME).absolutePath)
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