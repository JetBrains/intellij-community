// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager

object JpsGlobalEntitiesSerializers {
  const val SDK_FILE_NAME: String = "jdk.table"
  const val GLOBAL_LIBRARIES_FILE_NAME: String = "applicationLibraries"

  private val isSdkBridgeEnabled: Boolean = Registry.`is`("workspace.model.global.sdk.bridge", true)

  fun createApplicationSerializers(virtualFileUrlManager: VirtualFileUrlManager,
                                   sortedRootTypes: List<String>): List<JpsFileEntityTypeSerializer<WorkspaceEntity>> {
    val serializers = mutableListOf<JpsFileEntityTypeSerializer<WorkspaceEntity>>()
    if (isSdkBridgeEnabled) {
      serializers.add(createSdkSerializer(virtualFileUrlManager, sortedRootTypes) as JpsFileEntityTypeSerializer<WorkspaceEntity>)
    }

    val globalLibrariesFile = virtualFileUrlManager.getOrCreateFromUri(PathManager.getOptionsFile(GLOBAL_LIBRARIES_FILE_NAME).absolutePath)
    val globalLibrariesEntitySource = JpsGlobalFileEntitySource(globalLibrariesFile)
    serializers.add(JpsGlobalLibrariesFileSerializer(globalLibrariesEntitySource) as JpsFileEntityTypeSerializer<WorkspaceEntity>)

    return serializers
  }

  fun createSdkSerializer(virtualFileUrlManager: VirtualFileUrlManager, sortedRootTypes: List<String>): JpsSdkEntitySerializer {
    val globalSdkFile = virtualFileUrlManager.getOrCreateFromUri(PathManager.getOptionsFile(SDK_FILE_NAME).absolutePath)
    val globalSdkEntitySource = JpsGlobalFileEntitySource(globalSdkFile)
    return JpsSdkEntitySerializer(globalSdkEntitySource, sortedRootTypes)
  }
}

interface ApplicationStoreJpsContentReader {
  fun createContentWriter(): JpsAppFileContentWriter
  fun createContentReader(): JpsFileContentReader
}