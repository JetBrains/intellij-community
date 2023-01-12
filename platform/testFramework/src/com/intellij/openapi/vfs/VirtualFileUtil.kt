// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.SystemIndependent
import java.nio.file.Path


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

fun VirtualFile.getPsiFile(project: Project): PsiFile {
  return checkNotNull(findPsiFile(project)) {
    "Cannot find PSI file for $path"
  }
}

fun VirtualFile.getVirtualFile(relativePath: @SystemIndependent String): VirtualFile {
  return checkNotNull(findVirtualFile(relativePath)) {
    "File or directory doesn't exist: $path/$relativePath"
  }
}

fun VirtualFile.getVirtualDirectory(relativePath: @SystemIndependent String): VirtualFile {
  return checkNotNull(findVirtualDirectory(relativePath)) {
    "File or directory doesn't exist: $path/$relativePath"
  }
}

fun VirtualFile.createVirtualFile(relativePath: @SystemIndependent String): VirtualFile {
  check(findVirtualFile(relativePath) == null) {
    "File already exists: $path/$relativePath"
  }
  return findOrCreateVirtualFile(relativePath)
}

fun VirtualFile.createVirtualDirectory(relativePath: @SystemIndependent String): VirtualFile {
  check(findVirtualDirectory(relativePath) == null) {
    "Directory already exists: $path/$relativePath"
  }
  return findOrCreateVirtualDirectory(relativePath)
}

fun Path.getVirtualFile(): VirtualFile {
  return checkNotNull(findVirtualFile()) {
    "File or directory doesn't exist: $this"
  }
}

fun Path.getVirtualDirectory(): VirtualFile {
  return checkNotNull(findVirtualDirectory()) {
    "File or directory doesn't exist: $this"
  }
}
