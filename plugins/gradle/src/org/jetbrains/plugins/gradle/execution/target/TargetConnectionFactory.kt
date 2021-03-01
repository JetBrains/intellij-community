// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.target

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.TargetEnvironmentConfigurationProvider
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.ConnectionFactory
import org.gradle.tooling.internal.consumer.ConnectionParameters
import org.gradle.tooling.internal.consumer.Distribution
import org.gradle.tooling.internal.consumer.ProjectConnectionCloseListener

class TargetConnectionFactory(private val environmentConfigurationProvider: TargetEnvironmentConfigurationProvider,
                              private val taskId: ExternalSystemTaskId?,
                              private val taskListener: ExternalSystemTaskNotificationListener?) : ConnectionFactory(null, null, null) {
  override fun create(distribution: Distribution,
                      parameters: ConnectionParameters,
                      connectionCloseListener: ProjectConnectionCloseListener): ProjectConnection {
    return TargetProjectConnection(environmentConfigurationProvider, taskId, taskListener, parameters, connectionCloseListener)
  }
}