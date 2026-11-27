// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl.providers

// @spec ../../../../../../../../../spec/file-layers.spec.md

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem
import com.intellij.openapi.vfs.newvfs.VfsImplUtil
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.impl.JsonSettingsCodec
import com.intellij.platform.settings.v2.impl.SettingsDecodingException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

/** Reads and writes a settings [MapValue] as a JSON file at a file system [Path]. The calls block, so call them off the EDT. */
@Suppress("UseOptimizedEelFunctions", "SplitModeApiUsage") // Reads and writes local config files only. Remote file systems through EEL are not supported yet.
object JsonConfigFile {
  /**
   * Returns the map that the file at [path] holds, or an empty map when the file is absent.
   *
   * @param preferVfsContent whether to read the content that the VFS holds for the file. Set it only on a VFS event of
   *   the file. At that event, the VFS can already hold the save of an editor that the disk does not hold yet. Outside
   *   of an event, the VFS content can be older than the disk. The VFS content is read only when the VFS already knows
   *   the file and the file is valid. The lookup runs no VFS refresh (ADR 0167). In every other case, the disk is read.
   * @throws SettingsDecodingException when the file exists but does not parse
   */
  fun read(path: Path, preferVfsContent: Boolean = false): MapValue {
    val text = (if (preferVfsContent) readVfsContent(path) else null)
               ?: readDiskContent(path)
               ?: return MapValue(emptyMap())
    return JsonSettingsCodec.decode(text)
  }

  /**
   * Returns the content that the VFS holds for the file at [path], or `null` when the VFS does not know a valid file
   * there or cannot read its content.
   */
  private fun readVfsContent(path: Path): String? {
    val file = LocalFileSystem.getInstance().findFileByPathIfCached(FileUtil.toSystemIndependentName(path.toString()))
    if (file == null || !file.isValid) return null
    return try {
      file.contentsToByteArray().decodeToString()
    }
    catch (_: IOException) {
      null
    }
  }

  /** Returns the content of the file at [path] on the disk, or `null` when the file is absent. */
  private fun readDiskContent(path: Path): String? =
    try {
      Files.readString(path)
    }
    catch (_: NoSuchFileException) {
      null
    }

  /** Returns the JSON text that [write] stores for [map]. */
  fun toJson(map: MapValue): String = JsonSettingsCodec.encode(map)

  /** Writes [map] to the file at [path]. It creates the file and its missing directories on the first write (ADR 0152). */
  fun write(path: Path, map: MapValue) {
    Files.createDirectories(path.parent)
    Files.writeString(path, JsonSettingsCodec.encode(map))
    refreshInVfs(path)
  }

  /**
   * Makes the file at [path] known to the VFS, or its absence when the file is missing, so the watcher gets its
   * events. Two cases need it: the missing file at the watch start, whose directory the VFS has not listed yet, and
   * the file that a write creates through NIO in a directory the VFS listed before the write.
   *
   * The lookup runs on a background thread and never waits for the EDT (ADR 0167).
   */
  internal fun refreshInVfs(path: Path) {
    val vfs = LocalFileSystem.getInstance() as NewVirtualFileSystem
    VfsImplUtil.refreshAndFindFileByPath(vfs, FileUtil.toSystemIndependentName(path.toString())) { }
  }
}
