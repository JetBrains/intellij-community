package com.intellij.remoteServer.ir.target

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.NamedConfigurable
import com.intellij.remoteServer.ir.configuration.RemoteRunnerConfigurable
import com.intellij.remoteServer.ir.configuration.RemoteRunnerConfigurablesProvider
import com.intellij.remoteServer.ir.configuration.toNamedConfigurable

fun getRemoteRunnerConfigurables(project: Project?): List<RemoteRunnerConfigurable> =
  RemoteRunnerConfigurablesProvider.EXTENSION_NAME.extensionList
    .flatMap { provider ->
      provider.getConfigurables(project)
    }

fun getAdaptedRemoteRunnersConfigurables(project: Project?): List<NamedConfigurable<RemoteRunnerConfigurable>> =
  getRemoteRunnerConfigurables(project)
    .map { configurable ->
      (configurable to configurable).toNamedConfigurable()
    }