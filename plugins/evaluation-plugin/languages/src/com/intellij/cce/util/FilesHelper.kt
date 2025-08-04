// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.util

import com.intellij.cce.core.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.apache.commons.io.input.UnixLineEndingInputStream
import java.io.FileNotFoundException
import java.nio.file.Paths

object FilesHelper {
  fun getFilesOfLanguage(project: Project,
                         evaluationRoots: List<String>,
                         ignoreFileNames: Set<String>,
                         language: String?): List<VirtualFile> {
    val extensionToFiles = getFiles(evaluationRoots.map { getFile(project, it) }, ignoreFileNames)
    if (language == null) return extensionToFiles.flatMap { it.value }.toList()
    return extensionToFiles[language]?.toList() ?: throw IllegalArgumentException("No files for $language found")
  }

  private fun getFiles(evaluationRoots: List<VirtualFile>, ignoreFileNames: Set<String>): Map<String, Set<VirtualFile>> {
    val language2files = mutableMapOf<String, MutableSet<VirtualFile>>()
    for (file in evaluationRoots) {
      VfsUtilCore.iterateChildrenRecursively(file, { f -> !ignoreFileNames.contains(f.name) }, object : ContentIterator {
        override fun processFile(fileOrDir: VirtualFile): Boolean {
          val extension = fileOrDir.extension
          if (fileOrDir.isDirectory || extension == null) return true

          val language = Language.resolveByExtension(extension)
          language2files.computeIfAbsent(language.displayName) { mutableSetOf() }.add(fileOrDir)
          return true
        }
      })
    }
    return language2files
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