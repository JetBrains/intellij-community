// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.util.paths.RecursiveFilePathSet

/**
 * Wrapper for [RecursiveFilePathSet], allowing consistent lock-free access for reading operations
 *
 * Note that concurrent modifications still require additional locking
 */
internal class CopyOnWriteFilePathSet(private val caseSensitive: Boolean) {
  @Volatile
  private var filesSet: RecursiveFilePathSet = RecursiveFilePathSet(caseSensitive)

  var initialized: Boolean = false
    private set

  fun hasAncestor(file: FilePath): Boolean = filesSet.hasAncestor(file)

  fun containsExplicitly(file: FilePath): Boolean = filesSet.containsExplicitly(file)

  fun set(files: RecursiveFilePathSet) {
    filesSet = files
    initialized = true
  }

  fun clear() {
    filesSet = RecursiveFilePathSet(caseSensitive)
  }

  fun remove(pathsToExclude: Collection<FilePath>) {
    val newFiles = RecursiveFilePathSet(caseSensitive)
    filesSet.filePaths().forEach { file ->
      if (!pathsToExclude.contains(file)) {
        newFiles.add(file);
      }
    }
    filesSet = newFiles
  }

  fun add(pathsToAdd: Collection<FilePath>) {
    val newFiles = RecursiveFilePathSet(caseSensitive)
    newFiles.addAll(filesSet.filePaths())
    newFiles.addAll(pathsToAdd)

    filesSet = newFiles
  }

  fun toSet() = filesSet.filePaths().toMutableSet()
}