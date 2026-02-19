// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.serialization.impl

import com.intellij.openapi.application.PathManager
import com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource
import com.intellij.platform.workspace.storage.InternalEnvironmentName
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import java.nio.file.Path
import kotlin.io.path.absolutePathString

object JpsGlobalEntitiesSerializers {
  const val SDK_FILE_NAME: String = "jdk.table"
  const val GLOBAL_LIBRARIES_FILE_NAME: String = "applicationLibraries"

  /**
   * Returns application-level serializers for global entities.
   *
   * Note that the list of serializers must not intersect for different environments, otherwise the serialization behavior is undefined.
   */
  fun createApplicationSerializers(
    virtualFileUrlManager: VirtualFileUrlManager,
    sortedRootTypes: List<String>,
    optionsDir: Path,
    environmentName: InternalEnvironmentName,
  ): List<JpsFileEntityTypeSerializer<WorkspaceEntity>> {
    val serializers = mutableListOf<JpsFileEntityTypeSerializer<WorkspaceEntity>>()
    serializers.add(createSdkSerializer(virtualFileUrlManager, sortedRootTypes, optionsDir, environmentName) as JpsFileEntityTypeSerializer<WorkspaceEntity>)
    val environmentDir = resolveEnvironmentDir(optionsDir, environmentName)
    val globalLibrariesFile = virtualFileUrlManager.getOrCreateFromUrl(environmentDir.resolve(GLOBAL_LIBRARIES_FILE_NAME + PathManager.DEFAULT_EXT).absolutePathString())
    val globalLibrariesEntitySource = JpsGlobalFileEntitySource(globalLibrariesFile)
    serializers.add(JpsGlobalLibrariesFileSerializer(globalLibrariesEntitySource) as JpsFileEntityTypeSerializer<WorkspaceEntity>)

    return serializers
  }

  /**
   * Returns SDK serializer associated with the given [environmentName].
   * Each environment associated with its own `jdk.table.xml` file found within the directory by the [environmentName].
   * For the local environment, `jdk.table.xml` is stored directly in the [optionsDir].
   */
  fun createSdkSerializer(
    virtualFileUrlManager: VirtualFileUrlManager,
    sortedRootTypes: List<String>,
    optionsDir: Path,
    environmentName: InternalEnvironmentName,
  ): JpsSdkEntitySerializer {
    val environmentDir = resolveEnvironmentDir(optionsDir, environmentName)
    val globalSdkFile = virtualFileUrlManager.getOrCreateFromUrl(environmentDir.resolve(SDK_FILE_NAME + PathManager.DEFAULT_EXT).absolutePathString())
    val globalSdkEntitySource = JpsGlobalFileEntitySource(globalSdkFile)
    return JpsSdkEntitySerializer(globalSdkEntitySource, sortedRootTypes)
  }

  private fun resolveEnvironmentDir(optionsDir: Path, environmentName: InternalEnvironmentName): Path =
    when (environmentName) {
      InternalEnvironmentName.Local -> optionsDir
      is InternalEnvironmentName.Custom -> optionsDir.resolve(environmentName.name)
    }
}

interface ApplicationStoreJpsContentReader {
  fun createContentWriter(): JpsAppFileContentWriter
  fun createContentReader(): JpsFileContentReader
}