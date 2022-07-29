// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.projectsDataDir
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.io.exists
import com.intellij.util.io.lastModified
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files

internal class WorkspaceModelCachesInvalidatorBackgroundActivity : ProjectPostStartupActivity {
  override suspend fun execute(project: Project) {
    val invalidateCachesMarkerFile = WorkspaceModelCacheImpl.invalidateCachesMarkerFile
    if (!invalidateCachesMarkerFile.exists()) {
      return
    }

    val lastModifiedDate = invalidateCachesMarkerFile.lastModified()
    withContext(Dispatchers.IO) {
      projectsDataDir.directoryStreamIfExists { dirs ->
        dirs.asSequence()
          .map { it.resolve(WorkspaceModelCacheImpl.DATA_DIR_NAME) }
          .filter { it.exists() && it.lastModified() < lastModifiedDate }
          .forEach(NioFiles::deleteRecursively)
      }
      Files.deleteIfExists(invalidateCachesMarkerFile)
    }
  }
}