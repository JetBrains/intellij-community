// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl.providers

// @spec ../../../../../../../../../spec/file-layers.spec.md

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.util.PathUtilRt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Invokes [onChange] whenever a VFS event affects the file at [path], for the lifetime of [disposable]. The file can
 * be missing when the watch starts. The watch then reports the file when it appears, also together with a new
 * directory that holds it (ADR 0152).
 */
internal suspend fun watchConfigFile(disposable: Disposable, path: Path, onChange: () -> Unit) {
  withContext(Dispatchers.IO) { JsonConfigFile.refreshInVfs(path) }
  val target = ConfigFileMatcher(path)
  ApplicationManager.getApplication().messageBus.connect(disposable).subscribe(
    VirtualFileManager.VFS_CHANGES,
    object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        if (events.any { target.matches(it) }) onChange()
      }
    },
  )
}

/**
 * Matches VFS events against one target file. A VFS bulk batch can carry thousands of events, so the file name is
 * compared first (a cheap substring) and the full path only for the few survivors. Paths are normalized with
 * [FileUtilRt.toSystemIndependentName], because the event paths and the target [Path] can differ in separators. The
 * creation of a directory that holds the target also matches, because the VFS reports no separate event for the
 * files inside a new directory.
 */
private class ConfigFileMatcher(path: Path) {
  private val name: String = path.fileName.toString()
  private val independentPath: String = FileUtilRt.toSystemIndependentName(path.toString())

  fun matches(event: VFileEvent): Boolean {
    if (matches(event.path)) return true
    if (event is VFileCreateEvent && event.isDirectory) return holds(event.path)
    // A rename event carries the new path. A rename away from the watched path matches by its old path.
    return event is VFilePropertyChangeEvent && event.propertyName == VirtualFile.PROP_NAME && matches(event.oldPath)
  }

  private fun matches(eventPath: String): Boolean =
    PathUtilRt.getFileName(eventPath) == name && FileUtilRt.toSystemIndependentName(eventPath) == independentPath

  private fun holds(directoryPath: String): Boolean =
    independentPath.startsWith(FileUtilRt.toSystemIndependentName(directoryPath).trimEnd('/') + "/")
}
