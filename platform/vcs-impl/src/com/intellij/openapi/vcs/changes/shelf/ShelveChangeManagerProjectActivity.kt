// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

private class ShelveChangeManagerProjectActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    val manager = project.serviceAsync<ShelveChangesManager>()
    blockingContext {
      manager.projectOpened()
    }
  }
}