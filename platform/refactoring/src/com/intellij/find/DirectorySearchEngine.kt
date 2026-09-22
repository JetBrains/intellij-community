// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.util.Objects
import java.util.function.Consumer

/**
 * Supplies files and directories for Find in Files and Replace in Files.
 *
 * The caller uses this sequence for each search:
 * 1. It calls [canSearch] once for each engine and excludes engines that return `false`.
 * 2. For each directory, it calls [getWeight] on each remaining engine.
 * 3. It calls [searchDirectory] on the engine with the highest nonnegative weight.
 *
 * `DirectorySearchEngine` methods can run concurrently for different search requests.
 */
@ApiStatus.Experimental
interface DirectorySearchEngine {
  /**
   * Returns whether this engine can handle [findModel].
   * The caller invokes this method once for each search before it calls [getWeight] or [searchDirectory].
   * This method runs outside a read action.
   */
  fun canSearch(findModel: FindModel): Boolean

  /**
   * Returns a nonnegative weight if this engine can handle [directory] for the request, or a negative weight otherwise.
   * The engine with the highest weight handles the directory.
   * The choice among engines with equal weights is unspecified. The default engine has weight `0`.
   * This method runs inside or outside a read action.
   */
  fun getWeight(directory: VirtualFile): Int

  /**
   * Adds batches of descendants of [directory] to the shared search queue through [consumer].
   * The engine can expand one or more levels and must supply all files whose content on disk can match the request.
   * The engine does not need to inspect changes in unsaved documents - the platform adds files with unsaved changes to the search queue.
   * The engine can supply a directory to defer that directory's expansion to another engine.
   * The caller applies the file filters and searches the supplied files for occurrences.
   * All calls to [consumer] must finish before this method returns.
   * This method runs inside or outside a read action.
   */
  fun searchDirectory(directory: VirtualFile, findModel: FindModel, consumer: Consumer<in Collection<VirtualFile>>)

  @ApiStatus.Internal
  companion object {
    @JvmStatic
    fun selectDirectorySearchEngine(
      directory: VirtualFile,
      engines: List<DirectorySearchEngine>,
    ): DirectorySearchEngine {
      var bestEngine: DirectorySearchEngine? = null
      var bestWeight = -1
      for (engine in engines) {
        val weight = engine.getWeight(directory)
        if (weight > bestWeight) {
          bestEngine = engine
          bestWeight = weight
        }
      }
      return Objects.requireNonNull(bestEngine, "No directory search engine for $directory")!!
    }

    @ApiStatus.Internal
    @JvmField
    val EP_NAME: ExtensionPointName<DirectorySearchEngine> = ExtensionPointName.create("com.intellij.directorySearchEngine")
  }
}
