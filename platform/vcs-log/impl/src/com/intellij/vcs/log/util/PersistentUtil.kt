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
import com.intellij.vcs.log.util.PersistentUtil.LOG_CACHE
import com.intellij.vcs.log.util.PersistentUtil.deleteWithRenamingAllFilesStartingWith
import java.io.File
import java.io.IOException
import java.util.*

object PersistentUtil {
  @JvmField
  val LOG_CACHE = File(PathManager.getSystemPath(), "vcs-log")
  private const val CORRUPTION_MARKER = "corruption.marker"

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

  @Throws(IOException::class)
  @JvmStatic
  fun <T> createPersistentEnumerator(keyDescriptor: KeyDescriptor<T>, storageId: StorageId): PersistentEnumeratorBase<T> {
    val storageFile = storageId.getStorageFile()
    return IOUtil.openCleanOrResetBroken({ PersistentBTreeEnumerator(storageFile, keyDescriptor, Page.PAGE_SIZE, null, storageId.version) },
                                         storageFile)
  }

  internal fun deleteWithRenamingAllFilesStartingWith(baseFile: File): Boolean {
    val parentFile = baseFile.parentFile ?: return false

    val files = parentFile.listFiles { pathname -> pathname.name.startsWith(baseFile.name) } ?: return true

    var deleted = true
    for (f in files) {
      deleted = deleted and FileUtil.deleteWithRenaming(f)
    }
    return deleted
  }
}

class StorageId(private val subdirName: String,
                private val logId: String,
                val version: Int,
                private val features: BooleanArray) {
  private val safeLogId = PathUtilRt.suggestFileName(logId, true, true)

  constructor(subdirName: String, logId: String, version: Int) : this(subdirName, logId, version, booleanArrayOf())

  fun subdir() = File(LOG_CACHE, subdirName)

  private fun featuresSuffix(): String {
    if (features.isEmpty()) return ""
    return "." + features.map { if (it) 1 else 0 }.joinToString { it.toString() }
  }

  private fun getFile(kind: String = ""): File {
    val name: String = if (kind.isEmpty()) "$safeLogId.$version" else "$safeLogId.$kind.$version"
    return File(subdir(), "$name${featuresSuffix()}")
  }

  private fun getFileForMapIndexStorage(kind: String = ""): File {
    return MapIndexStorage.getIndexStorageFile(getFile(kind))
  }

  private fun iterateOverOtherFeatures(function: (BooleanArray) -> Unit) {
    if (features.isEmpty()) return

    val f = BooleanArray(features.size) { false }
    mainLoop@ while (true) {
      if (!features.contentEquals(f)) {
        function(f)
      }

      for (i in 0 until features.size) {
        if (!f[i]) {
          f[i] = true
          continue@mainLoop
        }
        f[i] = false
      }
      break@mainLoop
    }
  }

  @JvmOverloads
  fun getStorageFile(kind: String = ""): File {
    val storageFile = getFile(kind)
    if (!storageFile.exists()) {
      IOUtil.deleteAllFilesStartingWith(File(subdir(), safeLogId))
    }
    return storageFile
  }

  // do not forget to change cleanupStorageFiles method when editing this one
  fun getStorageFile(kind: String, forMapIndexStorage: Boolean = false): File {
    val storageFile = if (forMapIndexStorage) getFileForMapIndexStorage(kind) else getFile(kind)
    if (!storageFile.exists()) {
      for (oldVersion in 0 until version) {
        StorageId(subdirName, logId, oldVersion).cleanupStorageFiles(kind, forMapIndexStorage)
      }
      iterateOverOtherFeatures {
        StorageId(subdirName, logId, version, it).cleanupStorageFiles(kind, forMapIndexStorage)
      }
    }
    return getFile(kind)
  }

  private fun cleanupStorageFiles(kind: String, forMapIndexStorage: Boolean) {
    val oldStorageFile = if (forMapIndexStorage) getFileForMapIndexStorage(kind) else getFile(kind)
    IOUtil.deleteAllFilesStartingWith(oldStorageFile)
  }

  // this method cleans up all storage files for a project in a specified subdir
  // it assumes that these storage files all start with "safeLogId."
  // as method getStorageFile creates them
  // so these two methods should be changed in sync
  fun cleanupAllStorageFiles(): Boolean {
    return deleteWithRenamingAllFilesStartingWith(File(subdir(), "$safeLogId."))
  }
}
