package com.intellij.remoteServer.ir.configuration

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 * Provider that exposes [RemoteRunnerConfigurable] to expose user setup for
 * each [com.intellij.remoteServer.ir.IR.RemoteRunner] available.
 */
interface RemoteRunnerConfigurablesProvider {
  fun getConfigurables(project: Project?): List<RemoteRunnerConfigurable>

  companion object {
    val EXTENSION_NAME = ExtensionPointName.create<RemoteRunnerConfigurablesProvider>("com.intellij.ir.runnerConfigurablesProvider")
  }
}