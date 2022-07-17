// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil

@ApiStatus.Experimental
class GradleModuleData(private val dataNode: DataNode<out ModuleData>) {
  val moduleData = dataNode.data

  init {
    val systemId = moduleData.owner
    require(systemId == GradleConstants.SYSTEM_ID) { "Gradle module expected but '$systemId' module has been got." }
  }

  val moduleName = moduleData.moduleName
  val gradleProjectDir = moduleData.linkedExternalProjectPath
  val directoryToRunTask: String
    get() = moduleData.getDirectoryToRunTask()
  val gradlePath: String
    get() = moduleData.getGradlePath()
  val compositeBuildGradlePath: String
    get() = moduleData.getCompositeBuildGradlePath()
  val fullGradlePath: String
    get() = compositeBuildGradlePath + gradlePath
  val isBuildSrcModule: Boolean
    get() = moduleData.isBuildSrcModule()

  fun getTaskPath(simpleTaskName: String, prependCompositeBuildPath: Boolean = true): String {
    val path = if (prependCompositeBuildPath) fullGradlePath else gradlePath
    return "${path.trimEnd(':')}:$simpleTaskName"
  }

  fun <T> findAll(key: Key<T>): Collection<T> {
    return ExternalSystemApiUtil.findAll(dataNode, key).mapNotNull { it.data }
  }

  fun <T> find(key: Key<T>): T? {
    return ExternalSystemApiUtil.find(dataNode, key)?.data
  }
}

fun ModuleData.getDirectoryToRunTask() = getProperty("directoryToRunTask") ?: linkedExternalProjectPath
fun ModuleData.setDirectoryToRunTask(directoryToRunTask: String) = setProperty("directoryToRunTask", directoryToRunTask)

fun ModuleData.getGradlePath() = GradleProjectResolverUtil.getGradlePath(this)
fun ModuleData.getCompositeBuildGradlePath() = getProperty("compositeBuildGradlePath") ?: ""
fun ModuleData.setCompositeBuildGradlePath(compositeBuildGradlePath: String) =
  setProperty("compositeBuildGradlePath", compositeBuildGradlePath)

fun ModuleData.isBuildSrcModule() = getProperty("buildSrcModule")?.toBoolean() ?: false
fun ModuleData.setBuildSrcModule() = setProperty("buildSrcModule", true.toString())