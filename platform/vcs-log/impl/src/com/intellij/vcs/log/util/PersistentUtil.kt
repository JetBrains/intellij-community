/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.vcs.log.util

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtilRt
import com.intellij.util.indexing.impl.MapIndexStorage
import com.intellij.util.io.IOUtil
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.impl.VcsLogIndexer
import com.intellij.vcs.log.util.PersistentUtil.LOG_CACHE
import org.jetbrains.annotations.NonNls
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

object PersistentUtil {
  @JvmField
  val LOG_CACHE = File(PathManager.getSystemPath(), "vcs-log")

  @NonNls
  private const val CORRUPTION_MARKER = "corruption.marker"

  @JvmStatic
  val corruptionMarkerFile: File
    get() = File(LOG_CACHE, CORRUPTION_MARKER)

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
    return StringUtil.join(sortedRoots, { root -> root.path + "." + mapping(logProviders.getValue(root)) }, ".").hashCode()
  }

  @JvmStatic
  fun getProjectLogDataDirectoryName(projectName: String, logId: String): String =
    PathUtilRt.suggestFileName("${projectName.take(7)}.$logId", false, false)
}

class StorageId(@NonNls private val projectName: String,
                @NonNls private val subdirName: String,
                private val logId: String,
                val version: Int) {
  private val safeProjectName = PersistentUtil.getProjectLogDataDirectoryName(projectName, logId)
  val projectStorageDir by lazy { File(File(LOG_CACHE, safeProjectName), subdirName) }

  @JvmOverloads
  fun getStorageFile(kind: String, forMapIndexStorage: Boolean = false): Path {
    val storageFile = doGetRealStorageFile(kind, forMapIndexStorage)
    if (!Files.exists(storageFile)) {
      for (oldVersion in 0 until version) {
        val oldStorageFile = StorageId(projectName, subdirName, logId, oldVersion).doGetRealStorageFile(kind, forMapIndexStorage)
        IOUtil.deleteAllFilesStartingWith(oldStorageFile)
      }
    }
    return doGetStorageFile(kind) // MapIndexStorage itself adds ".storage" suffix to the given file, so we wont do it here
  }

  private fun doGetRealStorageFile(kind: String, forMapIndexStorage: Boolean): Path {
    val storageFile = doGetStorageFile(kind)
    if (!forMapIndexStorage) return storageFile
    return MapIndexStorage.getIndexStorageFile(storageFile)
  }

  private fun doGetStorageFile(kind: String): Path {
    return File(projectStorageDir, "$kind.$version").toPath()
  }

  fun cleanupAllStorageFiles(): Boolean {
    return FileUtil.deleteWithRenaming(projectStorageDir)
  }
}