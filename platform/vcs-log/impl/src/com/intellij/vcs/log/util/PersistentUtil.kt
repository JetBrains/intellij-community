// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.util

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtilRt
import com.intellij.util.indexing.impl.MapIndexStorage
import com.intellij.util.io.IOUtil
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.impl.VcsLogIndexer
import com.intellij.vcs.log.util.PersistentUtil.LOG_CACHE
import org.jetbrains.annotations.NonNls
import java.nio.file.Files
import java.nio.file.Path

object PersistentUtil {
  @JvmField
  val LOG_CACHE: Path = Path.of(PathManager.getSystemPath(), "vcs-log")

  @NonNls
  private const val CORRUPTION_MARKER: String = "corruption.marker"

  @JvmStatic
  val corruptionMarkerFile: Path
    get() = LOG_CACHE.resolve(CORRUPTION_MARKER)

  @JvmStatic
  fun calcLogId(project: Project, logProviders: Map<VirtualFile, VcsLogProvider>): String {
    val hashcode = calcHash(logProviders) { it.supportedVcs.name }
    return project.locationHash + "." + Integer.toHexString(hashcode)
  }

  @JvmStatic
  fun calcIndexId(project: Project, logProviders: Map<VirtualFile, VcsLogIndexer>): String {
    val hashcode = calcHash(logProviders) { it.supportedVcs.name }
    return project.locationHash + "." + Integer.toHexString(hashcode)
  }

  private fun <T> calcHash(logProviders: Map<VirtualFile, T>, mapping: (T) -> String): Int {
    val sortedRoots = logProviders.keys.sortedBy { it.path }
    return sortedRoots.joinToString(separator = ".") { root -> "${root.path}.${mapping(logProviders.getValue(root))}" }.hashCode()
  }

  @JvmStatic
  fun getProjectLogDataDirectoryName(projectName: String, logId: String): String {
    return PathUtilRt.suggestFileName("${projectName.take(7)}.$logId", false, false)
  }
}

internal class StorageId(@NonNls private val projectName: String,
                         @NonNls private val subdirName: String,
                         private val logId: String,
                         val version: Int) {
  private val safeProjectName = PersistentUtil.getProjectLogDataDirectoryName(projectName, logId)
  val projectStorageDir: Path by lazy { LOG_CACHE.resolve(safeProjectName).resolve(subdirName) }

  @JvmOverloads
  fun getStorageFile(kind: String, forMapIndexStorage: Boolean = false): Path {
    val storageFile = doGetRealStorageFile(kind, forMapIndexStorage)
    if (!Files.exists(storageFile)) {
      for (oldVersion in 0 until version) {
        val oldStorageFile = StorageId(projectName, subdirName, logId, oldVersion).doGetRealStorageFile(kind, forMapIndexStorage)
        IOUtil.deleteAllFilesStartingWith(oldStorageFile)
      }
    }
    return doGetStorageFile(kind) // MapIndexStorage itself adds ".storage" suffix to the given file, so we won't do it here
  }

  private fun doGetRealStorageFile(kind: String, forMapIndexStorage: Boolean): Path {
    val storageFile = doGetStorageFile(kind)
    if (!forMapIndexStorage) return storageFile
    return MapIndexStorage.getIndexStorageFile(storageFile)
  }

  private fun doGetStorageFile(kind: String): Path {
    return projectStorageDir.resolve("$kind.$version")
  }

  fun cleanupAllStorageFiles(): Boolean {
    return FileUtil.deleteWithRenaming(projectStorageDir)
  }
}