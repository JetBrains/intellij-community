// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.history

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@Experimental
interface FileHistoryPerformanceListener {

  companion object {
    @JvmStatic
    val EP_NAME = ExtensionPointName.create<FileHistoryPerformanceListener>("com.intellij.fileHistoryPerformanceListener")
  }

  fun onFileHistoryStarted(project: Project, path: FilePath)

  fun onFileHistoryFinished(project: Project, path: FilePath)
}