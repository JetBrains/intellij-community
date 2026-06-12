// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils

import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.testFramework.utils.coroutines.waitCoroutinesBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ActionsOnSaveTestUtil {
  companion object {
    @JvmStatic
    fun waitForActionsOnSaveToFinish(project: Project) {
      val service = project.service<ActionsOnSaveTestService>()
      service.cs.launch {
        ActionsOnSaveManager.getInstance(project).awaitPendingActions()
      }
      waitCoroutinesBlocking(service.cs)
    }
  }

  @Service(Service.Level.PROJECT)
  private class ActionsOnSaveTestService(val cs: CoroutineScope)
}
