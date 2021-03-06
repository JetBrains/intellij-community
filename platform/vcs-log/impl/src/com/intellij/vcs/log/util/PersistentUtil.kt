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
}

class StorageId(@NonNls private val projectName: String,
                @NonNls private val subdirName: String,
                private val logId: String,
                val version: Int) {
  private val safeLogId = PathUtilRt.suggestFileName(logId, true, true)
  private val safeProjectName = PathUtilRt.suggestFileName("${projectName.take(7)}.$logId", false, false)
  val subdir by lazy { File(File(LOG_CACHE, subdirName), safeProjectName) }

  // do not forget to change cleanupStorageFiles method when editing this one
  @JvmOverloads
  fun getStorageFile(kind: String, forMapIndexStorage: Boolean = false): Path {
    val storageFile = if (forMapIndexStorage) getFileForMapIndexStorage(kind) else getFile(kind)
    if (!storageFile.exists()) {
      for (oldVersion in 0 until version) {
        StorageId(projectName, subdirName, logId, oldVersion).cleanupStorageFiles(kind, forMapIndexStorage)
      }
      IOUtil.deleteAllFilesStartingWith(File(File(LOG_CACHE, subdirName), "$safeLogId."))
    }
    return getFile(kind).toPath()
  }

  private fun cleanupStorageFiles(kind: String, forMapIndexStorage: Boolean) {
    val oldStorageFile = if (forMapIndexStorage) getFileForMapIndexStorage(kind) else getFile(kind)
    IOUtil.deleteAllFilesStartingWith(oldStorageFile)
  }

  fun cleanupAllStorageFiles(): Boolean {
    return FileUtil.deleteWithRenaming(subdir)
  }

  private fun getFile(kind: String): File {
    return File(subdir, "$kind.$version")
  }

  private fun getFileForMapIndexStorage(kind: String = ""): File {
    return MapIndexStorage.getIndexStorageFile(getFile(kind).toPath()).toFile()
  }
}