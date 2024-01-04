// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.history

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.Hash
import org.jetbrains.annotations.ApiStatus.Internal
import kotlin.time.Duration

@Internal
interface FileHistoryPerformanceListener {

  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName.create<FileHistoryPerformanceListener>("com.intellij.fileHistoryPerformanceListener")
  }

  fun onFileHistoryFinished(project: Project, root: VirtualFile, path: FilePath, duration: Duration) {}

  suspend fun onFileHistoryFinished(project: Project, root: VirtualFile, path: FilePath, hash: Hash?, results: List<FileHistoryResult>) {}

  data class FileHistoryResult(val providerId: String, val duration: Duration)
}