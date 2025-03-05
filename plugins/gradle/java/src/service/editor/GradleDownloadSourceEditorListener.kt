// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.editor

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gradle.service.sources.GradleLibrarySourcesDownloader

class GradleDownloadSourceEditorListener(private val cs: CoroutineScope) : FileEditorManagerListener {

  override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    if (!AdvancedSettings.getBoolean("gradle.download.sources.automatically")) {
      return
    }
    cs.launch {
      val project = source.project
      if (GradleLibrarySourcesDownloader.canDownloadSources(project, file)) {
        GradleLibrarySourcesDownloader.download(project, file)
      }
    }
  }
}