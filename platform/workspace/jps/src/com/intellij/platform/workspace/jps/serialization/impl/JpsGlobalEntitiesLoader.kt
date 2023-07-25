// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.openapi.application.PathManager
import com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager

object JpsGlobalEntitiesSerializers {
  const val GLOBAL_LIBRARIES_FILE_NAME: String = "applicationLibraries"

  fun createApplicationSerializers(virtualFileUrlManager: VirtualFileUrlManager): List<JpsFileEntitiesSerializer<WorkspaceEntity>> {
    val globalLibrariesFile = virtualFileUrlManager.fromUrl(PathManager.getOptionsFile(GLOBAL_LIBRARIES_FILE_NAME).absolutePath)
    val globalLibrariesEntitySource = JpsGlobalFileEntitySource(globalLibrariesFile)

    val globalSdkFile = virtualFileUrlManager.fromUrl(PathManager.getOptionsFile("jdk.table").absolutePath)
    val globalSdkEntitySource = JpsGlobalFileEntitySource(globalSdkFile)
    val serializers = listOf(JpsGlobalLibrariesFileSerializer(globalLibrariesEntitySource), JpsSdkEntitySerializer(globalSdkEntitySource))
    return serializers as List<JpsFileEntitiesSerializer<WorkspaceEntity>>
  }
}

interface ApplicationStoreJpsContentReader {
  fun createContentReader(): JpsFileContentReader
}