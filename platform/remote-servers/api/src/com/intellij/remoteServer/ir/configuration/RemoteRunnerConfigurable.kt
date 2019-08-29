// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.ir.configuration

import com.intellij.execution.remote.target.IRExecutionTarget
import com.intellij.openapi.options.BoundConfigurable
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