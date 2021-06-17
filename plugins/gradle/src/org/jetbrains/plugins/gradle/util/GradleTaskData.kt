// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.task.TaskData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.util.text.nullize
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
class GradleTaskData(private val taskDataNode: DataNode<TaskData>, val gradleModulePath: String) {
  private val taskData = taskDataNode.data
  val name: String = taskData.name.run { substringAfterLast(':').nullize() ?: this }
  val description = taskData.description
  val isTest = taskData.isTest
  val isInherited = taskData.isInherited

  val isFromIncludedBuild by lazy {
    taskData.name.removePrefix(":").contains(":") &&
    (taskDataNode.parent?.data as? ModuleData)?.linkedExternalProjectPath != taskData.linkedExternalProjectPath
  }

  fun getFqnTaskName(): String {
    val taskPath = gradleModulePath.removeSuffix(":")
    val taskName = taskData.name.removePrefix(gradleModulePath).removePrefix(":")
    return "$taskPath:$taskName"
  }

  fun <T : Any?> find(key: Key<T>): T? = ExternalSystemApiUtil.find(taskDataNode, key)?.data
}