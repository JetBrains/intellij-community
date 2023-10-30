// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.util

import com.intellij.cce.core.Language
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.apache.commons.io.input.UnixLineEndingInputStream
import java.io.FileNotFoundException
import java.nio.file.Paths

object FilesHelper {
  fun getFilesOfLanguage(project: Project, evaluationRoots: List<String>, language: String): List<VirtualFile> {
    return getFiles(project, evaluationRoots.map { getFile(project, it) })[language]?.toList()
           ?: throw IllegalArgumentException("No files for $language found")
  }

  private fun getFiles(project: Project, evaluationRoots: List<VirtualFile>): Map<String, Set<VirtualFile>> {
    val language2files = mutableMapOf<String, MutableSet<VirtualFile>>()
    val index = ProjectRootManager.getInstance(project).fileIndex
    for (file in evaluationRoots) {
      //val filter = if (file.extension == "java") GlobalSearchScope.projectScope(project) else GlobalSearchScope.everythingScope(project)
      val filter = null
      VfsUtilCore.iterateChildrenRecursively(file, filter, object : ContentIterator {
        override fun processFile(fileOrDir: VirtualFile): Boolean {
          val extension = fileOrDir.extension
          if (fileOrDir.isDirectory || extension == null) return true

          val language = Language.resolveByExtension(extension)
          language2files.computeIfAbsent(language.displayName) { mutableSetOf() }.add(fileOrDir)
          return true
        }

        private fun shouldEvaluateOnFile(language: Language, fileOrDir: VirtualFile): Boolean {
          if (language == Language.JAVA || language == Language.KOTLIN) {
            return index.isInSourceContent(fileOrDir)
          }

          return true
        }
      })
    }
    return language2files
  }

  fun getLanguageByExtension(ext: String): com.intellij.lang.Language? {
    val fileType = FileTypeManager.getInstance().getFileTypeByExtension(ext) as? LanguageFileType ?: return null
    return fileType.language
  }

  fun getRelativeToProjectPath(project: Project, path: String): String {
    val projectPath = project.basePath
    return if (projectPath == null) path else Paths.get(projectPath).relativize(Paths.get(path)).toString()
  }

  fun getFile(project: Project, relativePath: String): VirtualFile =
    VfsUtil.findFile(Paths.get(project.basePath ?: "").resolve(relativePath), true)
    ?: throw FileNotFoundException("File not found: $relativePath")
}

fun VirtualFile.text(): String {
  return UnixLineEndingInputStream(this.inputStream, false).bufferedReader().use { it.readText() }
}