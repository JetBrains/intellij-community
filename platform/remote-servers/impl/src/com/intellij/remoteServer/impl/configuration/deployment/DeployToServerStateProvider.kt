// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteServer.impl.configuration.deployment

import com.intellij.execution.ExecutionException
import com.intellij.execution.Executor
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.ExtensionPointName.Companion.create
import com.intellij.remoteServer.configuration.RemoteServer
import com.intellij.remoteServer.configuration.deployment.DeploymentConfiguration
import com.intellij.remoteServer.configuration.deployment.DeploymentSource
import com.intellij.remoteServer.impl.runtime.DeployToServerState

interface DeployToServerStateProvider {
  @Throws(ExecutionException::class)
  fun getState(
    server: RemoteServer<*>,
    executor: Executor,
    env: ExecutionEnvironment,
    source: DeploymentSource,
    config: DeploymentConfiguration
  ): RunProfileState?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<DeployToServerStateProvider> = create(
      "com.intellij.remoteServer.deploymentConfiguration.stateProvider"
    )

    @JvmStatic
    fun getFirstNotNullState(
      server: RemoteServer<*>,
      executor: Executor,
      env: ExecutionEnvironment,
      source: DeploymentSource,
      config: DeploymentConfiguration
    ): RunProfileState {
      return EP_NAME.extensionList.firstNotNullOfOrNull {
        it.getState(server, executor, env, source, config)
      } ?: DeployToServerState(server, source, config, env)
    }
  }
}