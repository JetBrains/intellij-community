// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.sync

import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.execution.wsl.AbstractWslDistribution
import com.intellij.execution.wsl.sync.WslHashFilters.Companion.EMPTY_FILTERS
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import java.nio.file.Path
import java.util.concurrent.Future
import kotlin.io.path.exists
import kotlin.io.path.readText


/**
 * When copying files in parallel we must split them to the several chunks (each chunk is separate .tar file).
 * No reason to have less than [MIN_CHUNK_SIZE] files in one chunk: two files shouldn't be split to two chunks
 *
 * If source is windows and top level ``exec.txt`` file exists, it should contain list of top level files to mark +x on Linux side
 * (in case your helper needs to be executable)
 */
private const val MIN_CHUNK_SIZE = 1000

private val LOGGER = Logger.getInstance(WslSync::class.java)

class WslSync<SourceFile, DestFile> private constructor(private val source: FileStorage<SourceFile, DestFile>,
                                                        private val dest: FileStorage<DestFile, SourceFile>,
                                                        private val filters: WslHashFilters,
                                                        private val useStubs: Boolean) {

  companion object {

    /**
     * Synchronizes the given [windowsDir] and [linuxDir] (inside [distro]).
     * [linToWinCopy] determines the sync direction.
     * [filters] allow you to specify which files to include/exclude.
     * [useStubs] dictates whether empty stubs should be created for filtered out files.
     */
    @JvmOverloads
    fun syncWslFolders(linuxDir: String,
                       windowsDir: Path,
                       distro: AbstractWslDistribution,
                       linToWinCopy: Boolean = true,
                       filters: WslHashFilters = EMPTY_FILTERS,
                       useStubs: Boolean = false) {
      LOGGER.info("Sync " + if (linToWinCopy) "$linuxDir -> $windowsDir" else "$windowsDir -> $linuxDir")
      val win = WindowsFileStorage(windowsDir, distro)
      val lin = LinuxFileStorage(linuxDir, distro)
      if (linToWinCopy) {
        WslSync(lin, win, filters, useStubs)
      }
      else {
        WslSync(win, lin, filters, useStubs)
        val execFile = windowsDir.resolve("exec.txt")
        if (execFile.exists()) {
          // TODO: Support non top level files
          for (fileToMarkExec in execFile.readText().split(Regex("\\s+")).map { it.trim() }) {
            lin.markExec(fileToMarkExec)
          }
        }
      }
    }
  }

  @Service
  private class CoroutineScopeService(coroutineScope: CoroutineScope) : CoroutineScope by coroutineScope

  init {
    if (dest.isEmpty()) { //Shortcut: no need to sync anything, just copy everything
      LOGGER.info("Destination folder is empty, will copy all files")
      val syncData = source.calculateSyncData(filters, true, useStubs)
      copyFilesInParallel(syncData.hashes.map { it.file })
      syncLinks(syncData.links)
      syncStubs(syncData.stubs)
    }
    else {
      syncFoldersInternal()
    }
  }

  private fun syncLinks(sourceLinks: Map<FilePathRelativeToDir, FilePathRelativeToDir>,
                        destStubs: Map<FilePathRelativeToDir, FilePathRelativeToDir> = emptyMap()) {
    val linksToCreate = sourceLinks.filterNot { destStubs[it.key] == it.value }
    val linksToRemove = destStubs.filterNot { sourceLinks[it.key] == it.value }.keys

    LOGGER.info("Will create ${linksToCreate.size} links and remove ${linksToRemove.size}")
    dest.removeLinks(*linksToRemove.toTypedArray())
    dest.createSymLinks(linksToCreate)
  }

  private fun syncStubs(sourceStubs: Set<FilePathRelativeToDir>,
                        destStubs: Set<FilePathRelativeToDir> = emptySet()) {
    val stubsToCreate = sourceStubs.minus(destStubs)
    val stubsToRemove = destStubs.minus(sourceStubs)

    LOGGER.info("Will create ${stubsToCreate.size} links and remove ${stubsToRemove.size}")
    dest.createStubs(stubsToCreate)
    dest.removeFiles(stubsToRemove)
  }

  private fun syncFoldersInternal() {
    val sourceSyncDataFuture = service<CoroutineScopeService>()
      .async(ProcessIOExecutorService.INSTANCE.asCoroutineDispatcher()) {
        source.calculateSyncData(filters, false, useStubs)
      }
      .asCompletableFuture()
    val destSyncDataFuture = service<CoroutineScopeService>()
      .async(ProcessIOExecutorService.INSTANCE.asCoroutineDispatcher()) {
        dest.calculateSyncData(filters, false, useStubs)
      }
      .asCompletableFuture()

    val sourceSyncData = sourceSyncDataFuture.get()
    val sourceHashes = sourceSyncData.hashes.associateBy { it.fileLowerCase }.toMutableMap()
    val destSyncData = destSyncDataFuture.get()
    val destHashes = destSyncData.hashes

    val destFilesToRemove = ArrayList<FilePathRelativeToDir>(AVG_NUM_FILES)
    for (destRecord in destHashes) {
      // Lowercase is to ignore case when comparing files since Win is case-insensitive
      val sourceHashAndName = sourceHashes[destRecord.fileLowerCase]
      if (sourceHashAndName != null && sourceHashAndName.hash == destRecord.hash) {
        // Dest file matches Source file
        // Remove this record, so at the end there will be a list of files to copy from SRC to DST
        sourceHashes.remove(destRecord.fileLowerCase)
      }
      else if (sourceHashAndName == null) {
        // No such file on Source, remove it from Dest
        destFilesToRemove.add(destRecord.file) // Lin is case-sensitive so we must use real file name, not lowerecased as we used for cmp
      }
    }

    copyFilesInParallel(sourceHashes.values.map { it.file })
    dest.removeFiles(destFilesToRemove)
    syncLinks(sourceSyncData.links, destSyncData.links)
    syncStubs(sourceSyncData.stubs, destSyncData.stubs)
  }

  /**
   * Copies [filesToCopy] from src to dst
   * It may split files to the several chunks to copy them in parallel, see [MIN_CHUNK_SIZE]
   */
  private fun copyFilesInParallel(filesToCopy: Collection<FilePathRelativeToDir>) {
    if (filesToCopy.isEmpty()) {
      LOGGER.info("Nothing to copy: all files are same")
    }
    LOGGER.info("Will copy ${filesToCopy.size} files")
    // Copy files in parallel
    // 4 suggested by V.Lagunov and https://pkolaczk.github.io/disk-parallelism/
    val chunkSize = (filesToCopy.size / 4).coerceAtLeast(MIN_CHUNK_SIZE)
    val parts = filesToCopy.size / chunkSize
    if (parts == 0) {
      copyFilesToOtherSide(filesToCopy)
      //copyFilesFromSourceToDest(distribution, filesToCopy, linuxDir, windowsDir)
    }
    else {
      LOGGER.info("Split to $parts chunks")
      val futures = ArrayList<Future<*>>(parts)
      for (chunk in filesToCopy.chunked(chunkSize)) {
        futures += service<CoroutineScopeService>()
          .async(ProcessIOExecutorService.INSTANCE.asCoroutineDispatcher()) {
            copyFilesToOtherSide(chunk)
          }
          .asCompletableFuture()
      }
      futures.forEach { it.get() }
    }
    LOGGER.info("Copied")
  }

  private fun copyFilesToOtherSide(files: Collection<FilePathRelativeToDir>) {
    val destTar = dest.createTempFile()
    source.tarAndCopyTo(files, destTar)
    dest.unTar(destTar)
    dest.removeTempFile(destTar)
  }
}
