// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.startup.InitProjectActivity

private class DumbServiceStartupActivity : InitProjectActivity {
  override suspend fun run(project: Project) {
    val impl = project.serviceAsync<DumbService>() as DumbServiceImpl?
    if (impl == null) {
      logger<DumbServiceStartupActivity>().warn("Incorrect implementation of DumbService: ${DumbService.getInstance(project).javaClass.name}")
      return
    }
    impl.queueStartupActivitiesRequiredForSmartMode()
  }
}