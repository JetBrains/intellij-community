// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInsight.documentation

import com.intellij.codeEditor.JavaEditorFileSwapper
import com.intellij.codeInsight.documentation.actions.DocumentationDownloader
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.plugins.gradle.settings.GradleSettings
import org.jetbrains.plugins.gradle.util.GradleAttachSourcesProvider
import org.jetbrains.plugins.gradle.util.GradleBundle

class GradleDocumentationDownloader : DocumentationDownloader {

  override suspend fun canHandle(project: Project, file: VirtualFile): Boolean {
    if (!FileTypeRegistry.getInstance().isFileOfType(file, JavaClassFileType.INSTANCE)) {
      return false
    }
    if (GradleSettings.getInstance(project).linkedProjectsSettings.isEmpty()) {
      return false
    }
    return readAction { JavaEditorFileSwapper.findSourceFile(project, file) == null }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  override suspend fun download(project: Project, file: VirtualFile) {
    val libraryEntries = readAction { findLibraryEntriesForFile(file, project) }
    if (libraryEntries.isEmpty()) {
      return
    }
    val action = readAction {
      val psiFile = file.findPsiFile(project)
      if (psiFile == null) {
        return@readAction null
      }
      val actions = GradleAttachSourcesProvider()
        .getActions(libraryEntries, psiFile)
      return@readAction actions.firstOrNull()
    }
    if (action == null) {
      return
    }
    withBackgroundProgress(project = project, title = GradleBundle.message("gradle.action.download.sources.busy.text")) {
      suspendCancellableCoroutine<Nothing?> { continuation ->
        val callback = action.perform(libraryEntries)
        callback.doWhenProcessed {
          continuation.resume(null) { }
        }
      }
    }
  }

  private fun findLibraryEntriesForFile(file: VirtualFile, project: Project): List<LibraryOrderEntry> {
    val entries = mutableListOf<LibraryOrderEntry>()
    ProjectFileIndex.getInstance(project).getOrderEntriesForFile(file)
      .forEach {
        if (it is LibraryOrderEntry) {
          entries.add(it)
        }
      }
    return entries;
  }
}