// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.writeText

internal fun createProjectResourceFile(project: Project, relativePath: String, content: String): VirtualFile {
  val projectDirectory = requireNotNull(project.guessProjectDir()) { "Cannot determine project directory" }
  return runWriteActionAndWait {
    val parentRelativePath = relativePath.substringBeforeLast('/', "")
    val parentDirectory = if (parentRelativePath.isEmpty()) {
      projectDirectory
    }
    else {
      VfsUtil.createDirectoryIfMissing(projectDirectory, parentRelativePath)
    }
    val fileName = relativePath.substringAfterLast('/')
    val parent = requireNotNull(parentDirectory) { "Cannot create '$parentRelativePath'" }
    val file = parent.findChild(fileName) ?: parent.createChildData(project, fileName)
    file.writeText(content)
    file
  }
}

internal fun deleteProjectResourceFile(project: Project, file: VirtualFile) {
  runWriteActionAndWait {
    if (file.isValid) {
      file.delete(project)
    }
  }
}
