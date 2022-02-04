// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl.sync

import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.execution.wsl.AbstractWslDistribution
import com.intellij.openapi.diagnostic.Logger
import java.nio.file.Path
import java.util.concurrent.CompletableFuture.runAsync
import java.util.concurrent.CompletableFuture.supplyAsync
import java.util.concurrent.Future


/**
 * When copying files in parallel we must split them to the several chunks (each chunk is separate .tar file).
 * No reason to have less than [MIN_CHUNK_SIZE] files in one chunk: two files shouldn't be split to two chunks
 */
private const val MIN_CHUNK_SIZE = 1000

private val LOGGER = Logger.getInstance(WslSync::class.java)

class WslSync<SourceFile, DestFile> private constructor(private val source: FileStorage<SourceFile, DestFile>,
                                                        private val dest: FileStorage<DestFile, SourceFile>) {


  companion object {

    /**
     * Makes [windowsDir] reflect [linuxDir] (or vice versa depending on [linToWinCopy]) on [distribution] much like rsync.
     * Redundant files deleted, new/changed files copied.
     * Set [onlyExtensions] if you only care about certain extensions.
     * Direction depends on [linToWinCopy]
     */
    fun syncWslFolders(linuxDir: String,
                       windowsDir: Path,
                       distribution: AbstractWslDistribution,
                       linToWinCopy: Boolean = true,
                       onlyExtensions: Array<String> = emptyArray()) {
      val win = WindowsFileStorage(windowsDir, distribution, onlyExtensions)
      val lin = LinuxFileStorage(linuxDir, distribution, onlyExtensions)
      if (linToWinCopy) {
        WslSync(lin, win)
      }
      else {
        WslSync(win, lin)
      }
    }
  }

  init {
    if (dest.isEmpty()) { //Shortcut: no need to sync anything, just copy everything
      copyFilesInParallel(source.getAllFilesInDir())
    }
    else {
      syncFoldersInternal()
    }
  }

  private fun syncFoldersInternal() {
    val sourceHashesFuture = supplyAsync({
                                           source.getHashes()
                                         }, ProcessIOExecutorService.INSTANCE)
    val destHashesFuture = supplyAsync({
                                         dest.getHashes()
                                       }, ProcessIOExecutorService.INSTANCE)

    val sourceHashes: MutableMap<String, WslHashRecord> = sourceHashesFuture.get().associateBy { it.fileLowerCase }.toMutableMap()
    val destHashes: List<WslHashRecord> = destHashesFuture.get()

    val destFilesToRemove = ArrayList<String>(AVG_NUM_FILES)
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
  }

  /**
   * Copies [filesToCopy] from src to dst
   * It may split files to the several chunks to copy them in parallel, see [MIN_CHUNK_SIZE]
   */
  private fun copyFilesInParallel(filesToCopy: Collection<String>) {
    if (filesToCopy.isEmpty()) return
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
        futures += runAsync({
                              copyFilesToOtherSide(chunk)
                            }, ProcessIOExecutorService.INSTANCE)
      }
      futures.forEach { it.get() }
    }
    LOGGER.info("Copied")
  }

  private fun copyFilesToOtherSide(files: Collection<String>) {
    val destTar = dest.createTempFile()
    source.tarAndCopyTo(files, destTar)
    dest.unTar(destTar)
    dest.removeTempFile(destTar)
  }
}
