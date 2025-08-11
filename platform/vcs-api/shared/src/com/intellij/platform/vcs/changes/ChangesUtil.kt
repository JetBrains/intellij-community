// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.changes

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.util.containers.HashingStrategy
import com.intellij.util.containers.JBIterable
import com.intellij.util.containers.toArray
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
object ChangesUtil {
  @JvmField
  val CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY: HashingStrategy<FilePath?> = object : HashingStrategy<FilePath?> {
    override fun hashCode(path: FilePath?): Int {
      return if (path != null) Objects.hash(path.getPath(), path.isDirectory()) else 0
    }

    override fun equals(path1: FilePath?, path2: FilePath?): Boolean {
      if (path1 === path2) return true
      if (path1 == null || path2 == null) return false

      return path1.isDirectory() == path2.isDirectory() && path1.getPath() == path2.getPath()
    }
  }

  @JvmStatic
  fun getFilePath(change: Change): FilePath {
    val revision = change.afterRevision ?: change.beforeRevision
    requireNotNull(revision) { "Change $change doesn't have before or after revisions" }
    return revision.getFile()
  }

  @JvmStatic
  fun getAfterPath(change: Change): FilePath? = change.afterRevision?.getFile()

  @JvmStatic
  fun getBeforePath(change: Change): FilePath? = change.beforeRevision?.getFile()

  @JvmStatic
  fun equalsCaseSensitive(path1: FilePath?, path2: FilePath?): Boolean {
    return CASE_SENSITIVE_FILE_PATH_HASHING_STRATEGY.equals(path1, path2)
  }

  @JvmStatic
  fun getNavigatableArray(project: Project, files: Iterable<VirtualFile>): Array<Navigatable> {
    return files.asSequence()
      .filter { file -> !file.isDirectory() }
      .map { file -> OpenFileDescriptor(project, file) }
      .toList()
      .toArray(Navigatable.EMPTY_NAVIGATABLE_ARRAY)
  }

  @JvmStatic
  fun iteratePathsCaseSensitive(change: Change): JBIterable<FilePath> {
    val beforePath = getBeforePath(change)
    val afterPath = getAfterPath(change)

    if (equalsCaseSensitive(beforePath, afterPath)) {
      return JBIterable.of(beforePath)
    }
    else {
      return JBIterable.of(beforePath, afterPath).filterNotNull()
    }
  }
}