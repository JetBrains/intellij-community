// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl.providers

// @spec ../../../../../../../../../spec/file-layers.spec.md

import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.impl.JsonSettingsCodec
import com.intellij.platform.settings.v2.impl.SettingsDecodingException
import java.nio.file.Files
import java.nio.file.Path

/** Reads and writes a settings [MapValue] as a JSON file at a file system [Path]. The calls block, so call them off the EDT. */
@Suppress("UseOptimizedEelFunctions", "SplitModeApiUsage") // Reads and writes local config files only. Remote file systems through EEL are not supported yet.
object JsonConfigFile {
  /**
   * Returns the map that the file at [path] holds, or an empty map when the file is absent.
   *
   * @throws SettingsDecodingException when the file exists but does not parse
   */
  fun read(path: Path): MapValue =
    if (!Files.exists(path)) MapValue(emptyMap())
    else JsonSettingsCodec.decode(Files.readString(path))

  /** Returns the JSON text that [write] stores for [map]. */
  fun toJson(map: MapValue): String = JsonSettingsCodec.encode(map)

  /** Writes [map] to the file at [path]. It creates the file and its missing directories on the first write (ADR 0152). */
  fun write(path: Path, map: MapValue) {
    Files.createDirectories(path.parent)
    Files.writeString(path, JsonSettingsCodec.encode(map))
    refreshInVfs(path)
  }

  /**
   * Makes the file at [path] known to the VFS, or its absence when the file is missing. A VFS event fires only for a
   * file that the VFS tracks, and a refresh of a partly loaded directory checks only the names that the VFS knows. So
   * after the asynchronous refresh ends, a later VFS refresh reports a change of the file, and also its creation.
   */
  internal fun refreshInVfs(path: Path) {
    val vfm = VirtualFileManager.getInstance()
    // Refresh the parent's children first: a bare file-path refresh leaves a just-created file "absent" when the
    // directory's child list is already cached, so the new config would never become VFS-visible.
    // The finish callback runs on the EDT under the write lock, so its synchronous lookup runs inline.
    vfm.refreshAndFindFileByNioPath(path.parent)?.refresh(true, false) { vfm.refreshAndFindFileByNioPath(path) }
  }
}
