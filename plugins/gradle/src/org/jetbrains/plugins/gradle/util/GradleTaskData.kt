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
class GradleTaskData(val node: DataNode<TaskData>, val gradleModulePath: String) {
  val data = node.data
  val name = data.name.run { substringAfterLast(':').nullize() ?: this }
  val description = data.description
  val group = data.group
  val isTest = data.isTest
  val isInherited = data.isInherited

  val isFromIncludedBuild by lazy {
    data.name.removePrefix(":").contains(":") &&
    (node.parent?.data as? ModuleData)?.linkedExternalProjectPath != data.linkedExternalProjectPath
  }

  fun getFqnTaskName(): String {
    val taskPath = gradleModulePath.removeSuffix(":")
    val taskName = data.name.removePrefix(gradleModulePath).removePrefix(":")
    return "$taskPath:$taskName"
  }

  fun <T : Any?> find(key: Key<T>): T? = ExternalSystemApiUtil.find(node, key)?.data

  override fun toString() = getFqnTaskName()
}