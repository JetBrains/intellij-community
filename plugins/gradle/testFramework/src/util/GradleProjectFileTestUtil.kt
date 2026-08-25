// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleProjectFileTestUtil")

package com.intellij.gradle.testFramework.util

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.openapi.vfs.findOrCreateFile
import com.intellij.openapi.vfs.readText
import com.intellij.openapi.vfs.writeText
import com.intellij.testFramework.utils.editor.commitToPsi
import com.intellij.testFramework.utils.editor.reloadFromDisk
import com.intellij.testFramework.utils.vfs.getFile
import com.intellij.util.concurrency.annotations.RequiresWriteLock

/**
 * Root directory of an opened project.
 */
@Suppress("DEPRECATION")
fun Project.getProjectRoot(): VirtualFile = baseDir

/**
 * Returns an existing file at [relativePath] of the project [root][getProjectRoot], and fails if there is none.
 */
fun Project.getProjectFile(relativePath: String): VirtualFile =
  getProjectRoot().getFile(relativePath)

fun Project.findOrCreateProjectFile(relativePath: String): VirtualFile =
  runWriteActionAndWait {
    getProjectRoot().findOrCreateFile(relativePath)
  }

/**
 * Writes [text] into the file at [relativePath] of the project [root][getProjectRoot], creating the file if
 * needed, and commits the change to PSI.
 *
 * The document is reloaded from the disk beforehand because a document left over from a previous test would
 * otherwise shadow the content on the disk.
 */
fun Project.writeTextAndCommit(relativePath: String, text: String): VirtualFile {
  val file = findOrCreateProjectFile(relativePath)
  runWriteActionAndWait {
    file.writeTextAndCommit(this, text)
  }
  return file
}

fun Project.appendTextAndCommit(relativePath: String, text: String): VirtualFile {
  val file = getProjectFile(relativePath)
  runWriteActionAndWait {
    file.writeTextAndCommit(this, file.readText() + "\n" + text)
  }
  return file
}

fun Project.prependTextAndCommit(relativePath: String, text: String): VirtualFile {
  val file = getProjectFile(relativePath)
  runWriteActionAndWait {
    file.writeTextAndCommit(this, text + "\n" + file.readText())
  }
  return file
}

@RequiresWriteLock
private fun VirtualFile.writeTextAndCommit(project: Project, text: String) {
  findDocument()?.reloadFromDisk()
  writeText(text)
  findDocument()?.commitToPsi(project)
}
