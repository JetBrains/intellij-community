// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.startup.InitProjectActivity

internal class DumbServiceStartupActivity : InitProjectActivity {
  override suspend fun run(project: Project) {
    blockingContext {
      (DumbService.getInstance(project) as DumbServiceImpl).queueStartupActivitiesRequiredForSmartMode()
    }
  }
}