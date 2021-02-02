// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage.impl

import com.intellij.openapi.util.registry.Registry

enum class ConsistencyCheckingMode {
  DISABLED,
  ASYNCHRONOUS,
  SYNCHRONOUS;

  companion object {
    fun default(): ConsistencyCheckingMode =
      if (Registry.`is`("ide.new.project.model.strict.mode.rbs", true))
        ASYNCHRONOUS
      else
        DISABLED
  }
}
