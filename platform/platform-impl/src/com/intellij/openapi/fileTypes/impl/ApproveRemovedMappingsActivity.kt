// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl

import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity

private class ApproveRemovedMappingsActivity : ProjectPostStartupActivity {
  override suspend fun execute(project: Project) {
    val fileTypeManager = FileTypeManager.getInstance() as FileTypeManagerImpl
    DumbService.getInstance(project).unsafeRunWhenSmart {
      val removedMappings = fileTypeManager.removedMappingTracker
      removedMappings.approveUnapprovedMappings()
    }
  }
}