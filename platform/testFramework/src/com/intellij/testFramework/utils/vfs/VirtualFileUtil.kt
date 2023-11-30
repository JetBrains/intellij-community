// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.vfs

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.getResolvedPath
import com.intellij.openapi.vfs.*
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.annotations.SystemIndependent
import java.io.IOException
import java.nio.file.Path

@RequiresReadLock
fun VirtualFile.getDocument(): Document {
  return checkNotNull(findDocument()) {
    "Cannot find document for $path"
  }
}

@RequiresReadLock
fun VirtualFile.getPsiFile(project: Project): PsiFile {
  return PsiUtilCore.getPsiFile(project, this)
}

@RequiresReadLock
fun VirtualFile.getFile(relativePath: @SystemIndependent String): VirtualFile {
  val file = findFile(relativePath)
  if (file == null) {
    throw IOException("""
      |File doesn't exists: ${Path.of(path).getResolvedPath(relativePath)}
      |  basePath = $path
      |  relativePath = $relativePath
    """.trimMargin())
  }
  return file
}

@RequiresReadLock
fun VirtualFile.getDirectory(relativePath: @SystemIndependent String): VirtualFile {
  val directory = findDirectory(relativePath)
  if (directory == null) {
    throw IOException("""
      |Directory doesn't exists: ${Path.of(path).getResolvedPath(relativePath)}
      |  basePath = $path
      |  relativePath = $relativePath
    """.trimMargin())
  }
  return directory
}

@RequiresWriteLock
fun VirtualFile.createFile(relativePath: @SystemIndependent String): VirtualFile {
  val file = findFile(relativePath)
  if (file != null) {
    throw IOException("""
      |File already exists: $file
      |  basePath = $path
      |  relativePath = $relativePath
    """.trimMargin())
  }
  return findOrCreateFile(relativePath)
}

@RequiresWriteLock
fun VirtualFile.createDirectory(relativePath: @SystemIndependent String): VirtualFile {
  val directory = findDirectory(relativePath)
  if (directory != null) {
    throw IOException("""
      |Directory already exists: $directory
      |  basePath = $path
      |  relativePath = $relativePath
    """.trimMargin())
  }
  return findOrCreateDirectory(relativePath)
}

@RequiresWriteLock
fun VirtualFile.deleteRecursively() {
  delete(fileSystem)
}

@RequiresWriteLock
fun VirtualFile.deleteChildrenRecursively(predicate: (VirtualFile) -> Boolean) {
  children.filter(predicate).forEach { it.delete(fileSystem) }
}

@RequiresWriteLock
fun VirtualFile.deleteRecursively(relativePath: @SystemIndependent String) {
  findFileOrDirectory(relativePath)?.deleteRecursively()
}

@RequiresWriteLock
fun VirtualFile.deleteChildrenRecursively(relativePath: @SystemIndependent String, predicate: (VirtualFile) -> Boolean) {
  findFileOrDirectory(relativePath)?.deleteChildrenRecursively(predicate)
}

fun Path.refreshAndGetVirtualFile(): VirtualFile {
  val file = refreshAndFindVirtualFile()
  if (file == null) {
    throw IOException("File doesn't exist: $this")
  }
  return file
}

fun Path.refreshAndGetVirtualDirectory(): VirtualFile {
  val directory = refreshAndFindVirtualDirectory()
  if (directory == null) {
    throw IOException("Directory doesn't exist: $this")
  }
  return directory
}
