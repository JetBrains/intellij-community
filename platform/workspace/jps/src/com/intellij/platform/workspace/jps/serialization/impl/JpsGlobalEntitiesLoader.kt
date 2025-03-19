// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.openapi.application.PathManager
import com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import java.nio.file.Path
import kotlin.io.path.absolutePathString

object JpsGlobalEntitiesSerializers {
  const val SDK_FILE_NAME: String = "jdk.table"
  const val GLOBAL_LIBRARIES_FILE_NAME: String = "applicationLibraries"

  fun createApplicationSerializers(
    virtualFileUrlManager: VirtualFileUrlManager,
    sortedRootTypes: List<String>,
    optionsDir: Path
  ): List<JpsFileEntityTypeSerializer<WorkspaceEntity>> {
    val serializers = mutableListOf<JpsFileEntityTypeSerializer<WorkspaceEntity>>()
    serializers.add(createSdkSerializer(virtualFileUrlManager, sortedRootTypes, optionsDir) as JpsFileEntityTypeSerializer<WorkspaceEntity>)
    val globalLibrariesFile = virtualFileUrlManager.getOrCreateFromUrl(optionsDir.resolve(GLOBAL_LIBRARIES_FILE_NAME + PathManager.DEFAULT_EXT).absolutePathString())
    val globalLibrariesEntitySource = JpsGlobalFileEntitySource(globalLibrariesFile)
    serializers.add(JpsGlobalLibrariesFileSerializer(globalLibrariesEntitySource) as JpsFileEntityTypeSerializer<WorkspaceEntity>)

    return serializers
  }

  fun createSdkSerializer(virtualFileUrlManager: VirtualFileUrlManager, sortedRootTypes: List<String>, optionsDir: Path): JpsSdkEntitySerializer {
    val globalSdkFile = virtualFileUrlManager.getOrCreateFromUrl(optionsDir.resolve(SDK_FILE_NAME + PathManager.DEFAULT_EXT).absolutePathString())
    val globalSdkEntitySource = JpsGlobalFileEntitySource(globalSdkFile)
    return JpsSdkEntitySerializer(globalSdkEntitySource, sortedRootTypes)
  }
}

interface ApplicationStoreJpsContentReader {
  fun createContentWriter(): JpsAppFileContentWriter
  fun createContentReader(): JpsFileContentReader
}