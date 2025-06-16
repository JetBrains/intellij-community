// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.util

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtilRt
import com.intellij.util.indexing.impl.MapIndexStorage
import com.intellij.util.io.IOUtil
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.util.PersistentUtil.getPersistenceLogCacheDir
import org.jetbrains.annotations.NonNls
import java.io.IOException
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
    return project.locationHash + "." + Integer.toHexString(logProviders.calcHash())
  }

  private fun Map<VirtualFile, VcsLogProvider>.calcHash(): Int {
    val sortedRoots = keys.sortedBy { it.path }
    return sortedRoots.joinToString(separator = ".") { root -> "${root.path}.${getValue(root).supportedVcs.name}" }.hashCode()
  }

  @JvmStatic
  internal fun getPersistenceLogCacheDir(projectName: String, logId: String): Path {
    return LOG_CACHE.resolve(getProjectLogDataDirectoryName(projectName, logId))
  }

  @JvmStatic
  fun getProjectLogDataDirectoryName(projectName: String, logId: String): String {
    return PathUtilRt.suggestFileName("${projectName.take(7)}.$logId", false, false)
  }
}

internal sealed class StorageId(@NonNls protected val projectName: String, @NonNls protected val logId: String) {
  val baseDir: Path by lazy { getPersistenceLogCacheDir(projectName, logId) }
  abstract val storagePath: Path

  fun cleanupAllStorageFiles(): Boolean {
    val tempFileNameForDeletion = FileUtil.findSequentNonexistentFile(storagePath.parent.toFile(), storagePath.fileName.toString(), "")
    val dirToDelete = try {
      Files.move(storagePath, tempFileNameForDeletion.toPath())
    } catch (e: IOException) {
      Logger.getInstance(javaClass).warn("Failed to move $storagePath to $tempFileNameForDeletion", e)
      storagePath
    }

    return try {
      FileUtil.delete(dirToDelete)
      true
    } catch (e: IOException) {
      Logger.getInstance(javaClass).warn("Failed to delete $dirToDelete", e)
      false
    }
  }

  class Directory(
    projectName: String, @NonNls private val subdirName: String, logId: String,
    val version: Int,
  ) : StorageId(projectName, logId) {
    override val storagePath: Path by lazy { baseDir.resolve(subdirName) }

    @JvmOverloads
    fun getStorageFile(kind: String, forMapIndexStorage: Boolean = false): Path {
      val storageFile = doGetRealStorageFile(kind, forMapIndexStorage)
      if (!Files.exists(storageFile)) {
        for (oldVersion in 0 until version) {
          val oldStorageFile = Directory(projectName, subdirName, logId, oldVersion).doGetRealStorageFile(kind, forMapIndexStorage)
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
      return storagePath.resolve("$kind.$version")
    }
  }

  class File(projectName: String, logId: String, fileName: String, extension: String) : StorageId(projectName, logId) {
    override val storagePath: Path by lazy { baseDir.resolve("$fileName.$extension") }
  }
}
