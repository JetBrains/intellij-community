// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.projectsDataDir
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.io.exists
import com.intellij.util.io.lastModified

class WorkspaceModelCachesInvalidatorBackgroundActivity : StartupActivity.Background {
  override fun runActivity(project: Project) {
    if (!WorkspaceModelCacheImpl.invalidateCachesMarkerFile.exists()) return
    val lastModifiedDate = WorkspaceModelCacheImpl.invalidateCachesMarkerFile.lastModified()
    projectsDataDir.directoryStreamIfExists { dirs ->
      val filesToDelete = dirs.asSequence().map { it.resolve(WorkspaceModelCacheImpl.DATA_DIR_NAME) }
        .filter { it.exists() && it.lastModified().toMillis() < lastModifiedDate }
        .map { it.toFile() }.toList()
      FileUtil.asyncDelete(filesToDelete)
    }
    FileUtil.delete(WorkspaceModelCacheImpl.invalidateCachesMarkerFile)
  }
}