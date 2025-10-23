// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:Suppress("unused") // API
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.execution.build.CachedModuleDataFinder

private val LOG = Logger.getInstance(GradleModuleData::class.java)

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

  val gradlePathOrNull: String?
    get() = moduleData.gradlePathOrNull

  val gradleIdentityPathOrNull: String?
    get() = moduleData.gradleIdentityPathOrNull

  @Deprecated("Use gradlePathOrNull instead")
  val gradlePath: String
    get() = moduleData.gradlePath

  @Deprecated("Use gradleIdentityPathOrNull instead")
  val gradleIdentityPath: String
    get() = moduleData.gradleIdentityPath

  @Deprecated("Use gradleIdentityPathOrNull instead")
  val fullGradlePath: String
    get() = gradleIdentityPath

  val isBuildSrcModule: Boolean
    get() = moduleData.isBuildSrcModule()

  val isIncludedBuild: Boolean
    get() = moduleData.isIncludedBuild

  @Deprecated("Use 'getTaskPath(String) instead")
  fun getTaskPath(simpleTaskName: String, prependCompositeBuildPath: Boolean = true): String {
    return getTaskPath(simpleTaskName)
  }

  @JvmName("getTaskPathOfSimpleTaskName")
  fun getTaskPath(simpleTaskName: String): String {
    val identityPath = moduleData.gradleIdentityPath
    return if (identityPath.isEmpty() || identityPath == ":") {
      ":$simpleTaskName"
    }
    else {
      "$identityPath:$simpleTaskName"
    }
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

var ModuleData.gradlePath: String
  get() = getProperty("gradlePath") ?: throw IllegalStateException("Missing gradlePath on $id")
  set(value) = setProperty("gradlePath", value)

val ModuleData.gradlePathOrNull: String?
  get() = getProperty("gradlePath").also {
    if (it == null) {
      LOG.warn("gradlePath is null for ModuleData with id = $id")
    }
  }

/**
 * The path of the project in the current build setup.
 * In simplest cases, this just matches org.gradle.api.Project.getPath().
 * However, e.g. for composite builds, paths to projects will receive a 'composite prefix'.
 */
var ModuleData.gradleIdentityPath: String
  get() = getProperty("gradleIdentityPath") ?: throw IllegalStateException("Missing gradleIdentityPath on $id")
  set(value) = setProperty("gradleIdentityPath", value)

val ModuleData.gradleIdentityPathOrNull: String?
  get() = getProperty("gradleIdentityPath").also {
    if (it == null) {
      LOG.warn("gradleIdentityPath is null for ModuleData with id = $id")
    }
  }

var ModuleData.isIncludedBuild: Boolean
  get() = getProperty("isIncludedBuild")?.toBooleanStrictOrNull() ?: false
  set(value) = setProperty("isIncludedBuild", value.toString())

fun ModuleData.isBuildSrcModule() = getProperty("buildSrcModule")?.toBoolean() ?: false

fun ModuleData.setBuildSrcModule() = setProperty("buildSrcModule", true.toString())

private fun Module.findMainModuleDataNode(): DataNode<out ModuleData>? {
  return CachedModuleDataFinder.findMainModuleData(this)
}
