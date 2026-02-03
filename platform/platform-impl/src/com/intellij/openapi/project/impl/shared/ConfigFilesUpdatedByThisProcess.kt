// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl.shared

import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readBytes

private data class SavedFileData(val hash: Long, val size: Int)

private val deletedData = SavedFileData(hash = 0, size = -1)

/**
 * Stores information about files that were written to the config folder by this process. 
 * It's used to avoid unnecessary reloading on changes made by this process. 
 * Such reloads are unnecessary and may lead to problems because some configurations (e.g., [com.intellij.openapi.options.SchemeManager]) 
 * aren't updated automatically, so if one thread saves several configuration files, another may read inconsistent data. 
 */
internal class ConfigFilesUpdatedByThisProcess {
  @Volatile
  private var savedFiles = ConcurrentCollectionFactory.createConcurrentMap<String, SavedFileData>()

  /**
   * Contains data written by this process 
   */
  @Volatile
  private var oldSavedFiles = ConcurrentCollectionFactory.createConcurrentMap<String, SavedFileData>()
  
  private val hasher64 = Hashing.komihash5_0()

  fun saved(fileSpec: String, content: ByteArray) {
    savedFiles[fileSpec] = SavedFileData(hash = hasher64.hashBytesToLong(content), size = content.size)
  }
  
  fun deleted(fileSpec: String) {
    savedFiles[fileSpec] = deletedData
  }

  /**
   * This function is called periodically (once a minute) to clean up the old data.
   */
  fun cleanUpOldData() {
    if (oldSavedFiles.isNotEmpty() || savedFiles.isNotEmpty()) {
      LOG.trace("old data cleaned up")
      oldSavedFiles = savedFiles
      savedFiles = ConcurrentCollectionFactory.createConcurrentMap()
    }
  }

  /**
   * Returns `true` if the current content of [file] was written by this process.
   * @param fileSpec relative path to the file in [com.intellij.configurationStore.StreamProvider]'s format
   */
  fun wasWritten(fileSpec: String, file: Path): Boolean {
    val savedData = savedFiles[fileSpec] ?: oldSavedFiles[fileSpec] ?: return false
    try {
      val actualSize = Files.size(file)
      if (savedData.size.toLong() != actualSize) {
        LOG.trace { "file $fileSpec: stored size ${savedData.size}, but actual size is $actualSize" }
        return false
      }
      val actualHash = hasher64.hashBytesToLong(file.readBytes())
      if (savedData.hash != actualHash) {
        LOG.trace { "file $fileSpec: stored hash ${savedData.hash}, but actual hash is $actualHash" }
        return false
      }
      return true
    }
    catch (e: IOException) {
      LOG.trace(e)
    }
    return false
  }

  /**
   * Returns `true` if the file identified by [fileSpec] (in [com.intellij.configurationStore.StreamProvider]'s format) was deleted by this 
   * process.
   */
  fun wasDeleted(fileSpec: String): Boolean {
    val savedData = savedFiles[fileSpec] ?: oldSavedFiles[fileSpec]
    return savedData === deletedData
  }
}

private val LOG = logger<ConfigFilesUpdatedByThisProcess>()
