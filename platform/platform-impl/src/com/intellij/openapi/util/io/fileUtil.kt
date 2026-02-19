// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io

import com.intellij.openapi.application.EDT
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.ReadonlyStatusHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInAny
import java.io.File
import java.nio.file.AccessDeniedException
import java.util.concurrent.CancellationException

val File.systemIndependentPath: String
  get() = path.replace(File.separatorChar, '/')

fun endsWithName(path: String, name: String): Boolean {
  return path.endsWith(name) && (path.length == name.length || path.getOrNull(path.length - name.length - 1) == '/')
}

/**
 * Attempts to write a collection of files, handling [AccessDeniedException] by attempting to make files writable and write to file one more time.
 *
 * @param project the project context within which the files are handled; if null, no attempts to make files writable will be made
 * @param files a collection of file descriptors representing the files to be written
 * @param writeFile the actual file write operation
 * @param errorCollector a function that collects errors occurring during the write process
 * @see [com.intellij.openapi.vfs.ReadonlyStatusHandler.ensureFilesWritable]
 */
@ApiStatus.Internal
@CalledInAny
suspend fun <FileDescriptor> writeWithEnsureWritable(
  project: Project?,
  files: Collection<FileDescriptor>,
  writeFile: suspend (FileDescriptor) -> Unit,
  errorCollector: (FileDescriptor, Throwable) -> Unit,
) {
  val notWritableFiles = mutableMapOf<FileDescriptor, AccessDeniedException>()
  for (file in files) {
    try {
      writeFile(file)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: AccessDeniedException) {
      if (project == null) {
        errorCollector(file, e)
      }
      else {
        notWritableFiles[file] = e
      }
    }
    catch (e: Throwable) {
      errorCollector(file, e)
    }
  }

  if (project == null || notWritableFiles.isEmpty()) return

  val localFileSystem = LocalFileSystem.getInstance()
  val accessDeniedFiles = notWritableFiles.mapNotNull { (file, accessDeniedException) ->
    val filePath = accessDeniedException.file ?: return@mapNotNull null
    val virtualFile = localFileSystem.findFileByPath(filePath)
    when {
      virtualFile != null -> file to virtualFile
      else -> null
    }
  }.toMap()

  val status = withContext(Dispatchers.EDT) {
    ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(accessDeniedFiles.values)
  }

  val readOnlyFiles = status.readonlyFiles.toSet()

  val (stillReadOnly, writableFiles) = accessDeniedFiles.asSequence().partition { (_, file) -> file in readOnlyFiles }

  for ((file, _) in writableFiles) {
    try {
      writeFile(file)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: AccessDeniedException) {
      notWritableFiles[file] = e
    }
    catch (e: Throwable) {
      errorCollector(file, e)
    }
  }

  for ((file, _) in stillReadOnly) {
    var accessDeniedException = notWritableFiles[file]
    if (accessDeniedException != null) {
      errorCollector(file, accessDeniedException)
    }
  }
}
