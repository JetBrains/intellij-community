// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

private class ApproveRemovedMappingsActivity : ProjectActivity {
  override suspend fun execute(project: Project) : Unit = blockingContext {
    val fileTypeManager = FileTypeManager.getInstance() as FileTypeManagerImpl
    DumbService.getInstance(project).runWhenSmart {
      val removedMappings = fileTypeManager.removedMappingTracker
      removedMappings.approveUnapprovedMappings()
    }
  }
}