// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.openapi.application.PathManager
import com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager

object JpsGlobalEntitiesSerializers {
  const val SDK_FILE_NAME: String = "jdk.table"
  const val GLOBAL_LIBRARIES_FILE_NAME: String = "applicationLibraries"

  fun createApplicationSerializers(virtualFileUrlManager: VirtualFileUrlManager,
                                   sortedRootTypes: List<String>,
                                   createLibSerializer: Boolean): List<JpsFileEntitiesSerializer<WorkspaceEntity>> {
    val globalSdkFile = virtualFileUrlManager.fromUrl(PathManager.getOptionsFile(SDK_FILE_NAME).absolutePath)
    val globalSdkEntitySource = JpsGlobalFileEntitySource(globalSdkFile)

    val serializers = mutableListOf(JpsSdkEntitySerializer(globalSdkEntitySource, sortedRootTypes) as JpsFileEntitiesSerializer<WorkspaceEntity>)

    if (createLibSerializer) {
      val globalLibrariesFile = virtualFileUrlManager.fromUrl(PathManager.getOptionsFile(GLOBAL_LIBRARIES_FILE_NAME).absolutePath)
      val globalLibrariesEntitySource = JpsGlobalFileEntitySource(globalLibrariesFile)
      serializers.add(JpsGlobalLibrariesFileSerializer(globalLibrariesEntitySource) as JpsFileEntitiesSerializer<WorkspaceEntity>)
    }

    return serializers
  }
}

interface ApplicationStoreJpsContentReader {
  fun createContentReader(): JpsFileContentReader
}