// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.annotations.SystemIndependent
import java.nio.file.Path


@RequiresReadLock
fun VirtualFile.getDocument(): Document {
  return checkNotNull(findDocument()) {
    "Cannot find document for $path"
  }
}

fun Document.getVirtualFile(): VirtualFile {
  return checkNotNull(findVirtualFile()) {
    "Cannot find virtual file for $this"
  }
}

@RequiresReadLock
fun VirtualFile.getPsiFile(project: Project): PsiFile {
  return checkNotNull(findPsiFile(project)) {
    "Cannot find PSI file for $path"
  }
}

@RequiresReadLock
fun VirtualFile.getFile(relativePath: @SystemIndependent String): VirtualFile {
  return checkNotNull(findFile(relativePath)) {
    "File or directory doesn't exist: $path/$relativePath"
  }
}

@RequiresReadLock
fun VirtualFile.getDirectory(relativePath: @SystemIndependent String): VirtualFile {
  return checkNotNull(findDirectory(relativePath)) {
    "File or directory doesn't exist: $path/$relativePath"
  }
}

@RequiresWriteLock
fun VirtualFile.createFile(relativePath: @SystemIndependent String): VirtualFile {
  check(findFile(relativePath) == null) {
    "File already exists: $path/$relativePath"
  }
  return findOrCreateFile(relativePath)
}

@RequiresWriteLock
fun VirtualFile.createDirectory(relativePath: @SystemIndependent String): VirtualFile {
  check(findDirectory(relativePath) == null) {
    "Directory already exists: $path/$relativePath"
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

@RequiresWriteLock
fun Path.getVirtualFile(): VirtualFile {
  return checkNotNull(findVirtualFile()) {
    "File or directory doesn't exist: $this"
  }
}

@RequiresWriteLock
fun Path.getVirtualDirectory(): VirtualFile {
  return checkNotNull(findVirtualDirectory()) {
    "File or directory doesn't exist: $this"
  }
}
