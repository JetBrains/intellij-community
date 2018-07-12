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
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.indexing.impl.MapIndexStorage
import com.intellij.util.io.*
import com.intellij.vcs.log.VcsLogProvider
import java.io.File
import java.io.IOException
import java.util.*

object PersistentUtil {
  @JvmField
  val LOG_CACHE = File(PathManager.getSystemPath(), "vcs-log")
  private val CORRUPTION_MARKER = "corruption.marker"

  @JvmStatic
  val corruptionMarkerFile: File
    get() = File(LOG_CACHE, CORRUPTION_MARKER)

  @JvmStatic
  fun calcLogId(project: Project, logProviders: Map<VirtualFile, VcsLogProvider>): String {
    val hashcode = calcLogProvidersHash(logProviders)
    return project.locationHash + "." + Integer.toHexString(hashcode)
  }

  private fun calcLogProvidersHash(logProviders: Map<VirtualFile, VcsLogProvider>): Int {
    val sortedRoots = ContainerUtil.sorted(logProviders.keys, Comparator.comparing<VirtualFile, String> { it -> it.path })
    return StringUtil.join(sortedRoots, { root -> root.path + "." + logProviders[root]!!.supportedVcs.name }, ".").hashCode()
  }

  @JvmStatic
  fun getStorageFile(storageKind: String, logId: String, version: Int): File {
    val subdir = File(LOG_CACHE, storageKind)
    val safeLogId = PathUtilRt.suggestFileName(logId, true, true)
    val mapFile = File(subdir, "$safeLogId.$version")
    if (!mapFile.exists()) {
      IOUtil.deleteAllFilesStartingWith(File(subdir, safeLogId))
    }
    return mapFile
  }

  @Throws(IOException::class)
  @JvmStatic
  fun <T> createPersistentEnumerator(keyDescriptor: KeyDescriptor<T>,
                                     storageKind: String,
                                     logId: String,
                                     version: Int): PersistentEnumeratorBase<T> {
    val storageFile = getStorageFile(storageKind, logId, version)

    return IOUtil.openCleanOrResetBroken({ PersistentBTreeEnumerator(storageFile, keyDescriptor, Page.PAGE_SIZE, null, version) },
                                         storageFile)
  }

  private fun deleteWithRenamingAllFilesStartingWith(baseFile: File): Boolean {
    val parentFile = baseFile.parentFile ?: return false

    val files = parentFile.listFiles { pathname -> pathname.name.startsWith(baseFile.name) } ?: return true

    var deleted = true
    for (f in files) {
      deleted = deleted and FileUtil.deleteWithRenaming(f)
    }
    return deleted
  }

  // this method cleans up all storage files for a project in a specified subdir
  // it assumes that these storage files all start with "safeLogId."
  // as method getStorageFile creates them
  // so these two methods should be changed in sync
  @JvmStatic
  fun cleanupStorageFiles(subdirName: String, id: String): Boolean {
    val subdir = File(LOG_CACHE, subdirName)
    val safeLogId = PathUtilRt.suggestFileName(id, true, true)
    return deleteWithRenamingAllFilesStartingWith(File(subdir, "$safeLogId."))
  }

  // do not forget to change cleanupStorageFiles method when editing this one
  @JvmOverloads
  @JvmStatic
  fun getStorageFile(subdirName: String,
                     kind: String,
                     id: String,
                     version: Int,
                     forMapIndexStorage: Boolean = false): File {
    val subdir = File(LOG_CACHE, subdirName)
    val safeLogId = PathUtilRt.suggestFileName(id, true, true)
    val baseFile = getFileName(kind, subdir, safeLogId, version)
    val storageFile = if (forMapIndexStorage) MapIndexStorage.getIndexStorageFile(baseFile) else baseFile
    if (!storageFile.exists()) {
      for (oldVersion in 0 until version) {
        val baseOldStorageFile = getFileName(kind, subdir, safeLogId, oldVersion)
        val oldStorageFile = if (forMapIndexStorage) MapIndexStorage.getIndexStorageFile(baseOldStorageFile) else baseOldStorageFile
        IOUtil.deleteAllFilesStartingWith(oldStorageFile)
      }
    }
    return baseFile
  }

  private fun getFileName(kind: String, subdir: File, safeLogId: String, version: Int): File {
    return File(subdir, "$safeLogId.$kind.$version")
  }
}
