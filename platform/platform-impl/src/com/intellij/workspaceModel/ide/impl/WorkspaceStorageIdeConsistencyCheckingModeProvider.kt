// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.workspace.storage.impl.ConsistencyCheckingMode
import com.intellij.platform.workspace.storage.impl.ConsistencyCheckingModeProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class WorkspaceStorageIdeConsistencyCheckingModeProvider : ConsistencyCheckingModeProvider {
  override val mode: ConsistencyCheckingMode
    get() {
      val application = ApplicationManager.getApplication()
      return when {
        application != null && application.isUnitTestMode -> ConsistencyCheckingMode.ENABLED
        application != null && application.isEAP -> ConsistencyCheckingMode.ENABLED
        Registry.`is`("ide.new.project.model.strict.mode.rbs", false) -> ConsistencyCheckingMode.ENABLED
        else -> ConsistencyCheckingMode.DISABLED
      }
    }
}