// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.ir.configuration

import com.intellij.execution.ExecutionTarget
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import javax.swing.Icon

abstract class RemoteTargetType<C : RemoteTargetConfiguration>(val id: String) {

  abstract val displayName: String

  abstract val icon: Icon

  abstract fun createSerializer(config: C): PersistentStateComponent<*>

  abstract fun createDefaultConfig(): C

  abstract fun createRunnerConfigurable(project: Project, config: C): Configurable

  abstract fun createExecutionTarget(project: Project, config: C): ExecutionTarget?

  open val helpTopic: String? = null

  //TODO: suggest "predefined" configurations (e.g one per every configured SFTP connection)

  @Suppress("UNCHECKED_CAST")
  internal fun castConfiguration(config: RemoteTargetConfiguration) = config as C

  companion object {
    val EXTENSION_NAME = ExtensionPointName.create<RemoteTargetType<*>>("com.intellij.ir.targetType")

    fun findTargetType(id: String) = EXTENSION_NAME.extensionList.find { it.id == id }
  }
}