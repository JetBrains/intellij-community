// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl.cache

// @spec ../../../../../../../../../spec/snapshot-cache.spec.md

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getProjectDataPath
import com.intellij.platform.settings.v2.impl.cache.SettingsCacheStorage.markerFile
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Holds the file layout and the IO of the snapshot cache. The application cache is a single file in the system
 * directory of the application. Each project cache is a single file in the system data directory of that project, as
 * the workspace model cache is. It is never in `.idea`, because a cache is per machine and disposable.
 *
 * A single application-level [markerFile] carries the "Invalidate Caches" timestamp. Both the application load and a
 * project load check it: a cache older than the marker is ignored and deleted. So an invalidation is O(1). It touches
 * one marker and does not enumerate the project caches.
 */
object SettingsCacheStorage {
  private const val DIR: String = "settings-v2"
  private const val MARKER: String = ".invalidate"

  /** Returns the cache file of the application scope. */
  fun appCacheFile(): Path = PathManager.getSystemDir().resolve(DIR).resolve("app-cache.bin")

  /** Returns the cache file of [project]. */
  fun projectCacheFile(project: Project): Path = project.getProjectDataPath(DIR).resolve("cache.bin")

  private fun markerFile(): Path = PathManager.getSystemDir().resolve(DIR).resolve(MARKER)

  /**
   * Returns the bytes of [cacheFile], or `null` if the file is absent or invalidated. A file is invalidated when the
   * "Invalidate Caches" marker is newer. The method deletes an invalidated file.
   */
  fun read(cacheFile: Path): ByteArray? {
    if (!Files.exists(cacheFile)) return null
    val marker = markerFile()
    if (Files.exists(marker) && Files.getLastModifiedTime(marker) > Files.getLastModifiedTime(cacheFile)) {
      Files.deleteIfExists(cacheFile)
      return null
    }
    return Files.readAllBytes(cacheFile)
  }

  /** Writes [bytes] to [cacheFile] atomically (temp file + move), creating parent directories. */
  fun write(cacheFile: Path, bytes: ByteArray) {
    Files.createDirectories(cacheFile.parent)
    val tmp = Files.createTempFile(cacheFile.parent, "cache", ".tmp")
    try {
      Files.write(tmp, bytes)
      try {
        Files.move(tmp, cacheFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      }
      catch (e: AtomicMoveNotSupportedException) {
        Files.move(tmp, cacheFile, StandardCopyOption.REPLACE_EXISTING)
      }
    }
    finally {
      Files.deleteIfExists(tmp)
    }
  }

  /**
   * Stamps the shared marker with the current time, so the next load ignores every existing cache: the application
   * cache and each project cache.
   */
  fun invalidateAll() {
    val marker = markerFile()
    Files.createDirectories(marker.parent)
    Files.write(marker, ByteArray(0))
  }
}
