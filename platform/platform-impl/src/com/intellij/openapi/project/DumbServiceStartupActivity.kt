// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.startup.InitProjectActivity

internal class DumbServiceStartupActivity : InitProjectActivity {
  override suspend fun run(project: Project) {
    val dumbService = DumbService.getInstance(project) as DumbServiceImpl
    // todo remove wrapping and fix deadlock in headless mode (run "IDEA - generate js predefined shared index)
    ApplicationManager.getApplication().executeOnPooledThread {
      dumbService.queueStartupActivitiesRequiredForSmartMode()
    }
  }
}