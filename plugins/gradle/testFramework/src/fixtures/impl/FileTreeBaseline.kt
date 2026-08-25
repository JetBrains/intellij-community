// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.testFramework.fixtures.impl

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.TreeMap

/**
 * One difference of a working tree from its baseline.
 * [relativePath] is relative to the root of each tree.
 */
internal sealed interface FileTreeDifference {

  val relativePath: Path

  /**
   * The working tree has an entry that the baseline does not have. The entry is a file, or a directory with all its content.
   */
  data class ExtraEntry(override val relativePath: Path) : FileTreeDifference

  data class MissingDirectory(override val relativePath: Path) : FileTreeDifference

  data class MissingFile(override val relativePath: Path) : FileTreeDifference

  /**
   * Both trees have the file, and its bytes differ.
   */
  data class ChangedFile(override val relativePath: Path) : FileTreeDifference
}

/**
 * Compares [workingTree] with [baseline].
 *
 * The names are compared with case, also on a file system that ignores the case.
 * An entry that is a file in one tree and a directory in the other tree is one extra entry and one missing entry.
 * A missing directory is followed by each of its entries.
 *
 * @param isExcluded gets a path relative to the root of the trees. An excluded entry is skipped in both trees, with all its content.
 */
internal fun findDifferences(baseline: Path, workingTree: Path, isExcluded: (Path) -> Boolean): List<FileTreeDifference> {
  val differences = ArrayList<FileTreeDifference>()
  collectDifferences(baseline, workingTree, null, isExcluded, differences)
  return differences
}

internal fun areTreesEqual(first: Path, second: Path, isExcluded: (Path) -> Boolean): Boolean {
  return findDifferences(first, second, isExcluded).isEmpty()
}

private fun collectDifferences(
  baseline: Path,
  workingTree: Path,
  relativeDirectory: Path?,
  isExcluded: (Path) -> Boolean,
  differences: MutableList<FileTreeDifference>,
) {
  val baselineEntries = listEntries(baseline, relativeDirectory, isExcluded)
  val workingEntries = listEntries(workingTree, relativeDirectory, isExcluded)
  for ([name, entry] in workingEntries) {
    if (baselineEntries[name]?.isDirectory != entry.isDirectory) {
      differences.add(FileTreeDifference.ExtraEntry(entry.relativePath))
    }
  }
  for ([name, entry] in baselineEntries) {
    val relativePath = entry.relativePath
    when {
      workingEntries[name]?.isDirectory != entry.isDirectory -> {
        collectMissingEntries(baseline, entry, isExcluded, differences)
      }
      entry.isDirectory -> {
        collectDifferences(baseline, workingTree, relativePath, isExcluded, differences)
      }
      !areFilesEqual(baseline.resolve(relativePath), workingTree.resolve(relativePath)) -> {
        differences.add(FileTreeDifference.ChangedFile(relativePath))
      }
    }
  }
}

private fun collectMissingEntries(baseline: Path, entry: Entry, isExcluded: (Path) -> Boolean, differences: MutableList<FileTreeDifference>) {
  if (!entry.isDirectory) {
    differences.add(FileTreeDifference.MissingFile(entry.relativePath))
    return
  }
  differences.add(FileTreeDifference.MissingDirectory(entry.relativePath))
  for (child in listEntries(baseline, entry.relativePath, isExcluded).values) {
    collectMissingEntries(baseline, child, isExcluded, differences)
  }
}

private class Entry(val relativePath: Path, val isDirectory: Boolean)

/**
 * @return the entries of the directory by name, in the order of the names. The result is empty when the directory does not exist.
 */
private fun listEntries(root: Path, relativeDirectory: Path?, isExcluded: (Path) -> Boolean): Map<String, Entry> {
  val directory = if (relativeDirectory == null) root else root.resolve(relativeDirectory)
  val entries = TreeMap<String, Entry>()
  if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
    return entries
  }
  Files.newDirectoryStream(directory).use { stream ->
    for (path in stream) {
      val relativePath = root.relativize(path)
      if (!isExcluded(relativePath)) {
        val name = path.fileName.toString()
        entries[name] = Entry(relativePath, Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
      }
    }
  }
  return entries
}

private fun areFilesEqual(first: Path, second: Path): Boolean {
  return Files.size(first) == Files.size(second) && Files.mismatch(first, second) == -1L
}
