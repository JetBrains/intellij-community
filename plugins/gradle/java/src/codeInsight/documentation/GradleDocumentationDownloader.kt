// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.codeInsight.documentation

import com.intellij.codeEditor.JavaEditorFileSwapper
import com.intellij.codeInsight.documentation.actions.DocumentationDownloader
import com.intellij.ide.highlighter.JavaClassFileType
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.plugins.gradle.service.sources.GradleLibrarySourcesDownloader
import org.jetbrains.plugins.gradle.settings.GradleSettings

class GradleDocumentationDownloader : DocumentationDownloader {

  override suspend fun canHandle(project: Project, file: VirtualFile): Boolean {
    if (!FileTypeRegistry.getInstance().isFileOfType(file, JavaClassFileType.INSTANCE)) {
      return false
    }
    if (GradleSettings.getInstance(project).linkedProjectsSettings.isEmpty()) {
      return false
    }
    if (!GradleLibrarySourcesDownloader.canDownloadSources(project, file)) {
      return false
    }
    return readAction { JavaEditorFileSwapper.findSourceFile(project, file) == null }
  }

  override suspend fun download(project: Project, file: VirtualFile): Boolean {
    return GradleLibrarySourcesDownloader.download(project, file) != null
  }
}