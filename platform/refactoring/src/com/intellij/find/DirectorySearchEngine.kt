// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.function.Consumer
import kotlin.io.path.name

/**
 * Supplies files and directories for content and name searches.
 *
 * The caller uses this sequence for each content search:
 * 1. It calls [canSearch] once for each engine and excludes engines that return `false`.
 * 2. For each directory, it calls [getWeight] on each remaining engine.
 * 3. It calls [searchDirectory] on the engine with the highest nonnegative weight.
 *
 * For each name search, the caller calls [canSearchNames] once per engine.
 * It then selects an engine with [getWeight] for each directory and calls [searchNames].
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
   * Returns whether this engine can search for file and directory names.
   * The caller invokes this method once for each name search before it calls [getWeight] or [searchNames].
   * This method runs outside a read action.
   */
  fun canSearchNames(): Boolean

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

  /**
   * Searches [directory] and its descendants for files and directories that may match [pathPattern].
   * The engine can also send a candidate for [directory] to [consumer].
   *
   * [pathPattern] is a fuzzy path pattern without a leading or trailing slash.
   * It uses `/` as a path separator and can contain partial names, `*` wildcards, and spaces.
   * The caller converts backslashes to slashes and removes line and column suffixes.
   * The path can start at a search root above [directory].
   * Matching ignores case and allows other path components between the pattern's components.
   *
   * Send every possible match to [consumer]. The caller checks the results and accepts extra candidates.
   * All calls to [consumer] must finish before this method returns.
   */
  fun searchNames(directory: VirtualFile, pathPattern: String, consumer: Consumer<FileSearchCandidate>)

  /** Holds a file or a path until the caller needs a [VirtualFile]. */
  sealed interface FileSearchCandidate {
    val name: String

    /** Returns the file, or `null` if resolution fails. Resolve a path outside a read action. */
    fun resolveVirtualFile(): VirtualFile?

    /** A candidate supplied as a path. */
    sealed interface FromPath : FileSearchCandidate {
      val path: Path
    }

    /** A candidate supplied as a file. */
    sealed interface FromVirtualFile : FileSearchCandidate {
      val file: VirtualFile
    }

    companion object {
      /** Stores [path] as an absolute path without resolving it in VFS. The path must use the default file system. */
      @JvmStatic
      fun fromPath(path: Path): FromPath {
        return PathFileSearchCandidate(path)
      }

      /** Uses a file that the caller already has. */
      @JvmStatic
      fun fromVirtualFile(file: VirtualFile): FromVirtualFile = VirtualFileSearchCandidate(file)
    }
  }

  @ApiStatus.Internal
  companion object {
    @JvmStatic
    fun selectDirectorySearchEngine(
      directory: VirtualFile,
      engines: List<DirectorySearchEngine>,
    ): DirectorySearchEngine? {
      var bestEngine: DirectorySearchEngine? = null
      var bestWeight = -1
      for (engine in engines) {
        val weight = engine.getWeight(directory)
        if (weight > bestWeight) {
          bestEngine = engine
          bestWeight = weight
        }
      }
      return bestEngine
    }

    @ApiStatus.Internal
    @JvmField
    val EP_NAME: ExtensionPointName<DirectorySearchEngine> = ExtensionPointName.create("com.intellij.directorySearchEngine")
  }
}

private class PathFileSearchCandidate(override val path: Path) : DirectorySearchEngine.FileSearchCandidate.FromPath {
  private var virtualFile: VirtualFile? = null
  override val name: String = path.name

  override fun resolveVirtualFile(): VirtualFile? {
    virtualFile?.takeIf { it.isValid }?.let { return it }
    return LocalFileSystem.getInstance().findFileByPathWithoutCaching(path.toString())
      ?.takeIf { it.isValid }
      .also { virtualFile = it }
  }

  override fun toString(): String = "FromPath(path=$path)"
}

private class VirtualFileSearchCandidate(override val file: VirtualFile) : DirectorySearchEngine.FileSearchCandidate.FromVirtualFile {
  override val name: String get() = file.name

  override fun resolveVirtualFile(): VirtualFile? = file.takeIf { it.isValid }

  override fun toString(): String = "FromVirtualFile(file=$file)"
}
