package com.intellij.remoteServer.ir.configuration

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.remoteServer.ir.target.IRExecutionTarget
import javax.swing.Icon

abstract class RemoteRunnerConfigurable(displayName: String) : BoundConfigurable(displayName, "") {
  /**
   * Creates specific [IRExecutionTarget] based on the configuration of this
   * [RemoteRunnerConfigurable].
   */
  // TODO we might want to separate [RemoteRunnerConfigurable] from [IR.RemoteRunner]
  abstract fun createExecutionTarget(): IRExecutionTarget

  abstract fun getIcon() : Icon?
}