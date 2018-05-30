// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.history

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vcs.FilePath
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.data.index.VcsLogPathsIndex
import gnu.trove.TIntObjectHashMap
import gnu.trove.TIntProcedure

abstract class FileNamesData {
  private val commitToPathAndChanges = TIntObjectHashMap<MutableMap<FilePath, MutableMap<Int, VcsLogPathsIndex.ChangeData?>>>()
  private var hasRenames = false

  val commits: Set<Int>
    get() {
      val result = ContainerUtil.newHashSet<Int>()
      commitToPathAndChanges.forEach(TIntProcedure { result.add(it) })
      return result
    }

  protected abstract fun getPathById(pathId: Int): FilePath

  fun hasRenames(): Boolean {
    return hasRenames
  }

  fun add(commit: Int,
          path: FilePath,
          changes: List<VcsLogPathsIndex.ChangeData?>,
          parents: List<Int>) {
    var pathToChanges: MutableMap<FilePath, MutableMap<Int, VcsLogPathsIndex.ChangeData?>>? = commitToPathAndChanges.get(commit)
    if (pathToChanges == null) {
      pathToChanges = ContainerUtil.newHashMap<FilePath, MutableMap<Int, VcsLogPathsIndex.ChangeData?>>()
      commitToPathAndChanges.put(commit, pathToChanges)
    }

    if (!hasRenames) {
      for (data in changes) {
        if (data == null) continue
        if (data.isRename) {
          hasRenames = true
          break
        }
      }
    }

    var parentToChangesMap: MutableMap<Int, VcsLogPathsIndex.ChangeData?>? = pathToChanges[path]
    if (parentToChangesMap == null) parentToChangesMap = ContainerUtil.newHashMap<Int, VcsLogPathsIndex.ChangeData?>()
    if (!parents.isEmpty()) {
      LOG.assertTrue(parents.size == changes.size)
      for (i in changes.indices) {
        val existing = parentToChangesMap[parents[i]]
        if (existing != null) {
          // since we occasionally reindex commits with different rename limit
          // it can happen that we have several change data for a file in a commit
          // one with rename, other without
          // we want to keep a renamed-one, so throwing the other one out
          if (existing.isRename) continue
        }
        parentToChangesMap[parents[i]] = changes[i]
      }
    }
    else {
      // initial commit
      LOG.assertTrue(changes.size == 1)
      parentToChangesMap[-1] = changes[0]
    }
    pathToChanges[path] = parentToChangesMap
  }

  fun getPathInParentRevision(commit: Int, parent: Int, childPath: FilePath): FilePath? {
    val filesToChangesMap = commitToPathAndChanges.get(commit)
    LOG.assertTrue(filesToChangesMap != null, "Missing commit $commit")
    val changes = filesToChangesMap!![childPath] ?: return childPath

    val change = changes[parent]
    if (change == null) {
      LOG.assertTrue(changes.size > 1)
      return childPath
    }
    if (change.kind == VcsLogPathsIndex.ChangeKind.RENAMED_FROM) return null
    return if (change.kind == VcsLogPathsIndex.ChangeKind.RENAMED_TO) {
      getPathById(change.otherPath)
    }
    else childPath
  }

  fun getPathInChildRevision(commit: Int, parentIndex: Int, parentPath: FilePath): FilePath? {
    val filesToChangesMap = commitToPathAndChanges.get(commit)
    LOG.assertTrue(filesToChangesMap != null, "Missing commit $commit")
    val changes = filesToChangesMap!![parentPath] ?: return parentPath

    val change = changes[parentIndex] ?: return parentPath
    if (change.kind == VcsLogPathsIndex.ChangeKind.RENAMED_TO) return null
    return if (change.kind == VcsLogPathsIndex.ChangeKind.RENAMED_FROM) {
      getPathById(change.otherPath)
    }
    else parentPath
  }

  fun affects(id: Int, path: FilePath): Boolean {
    return commitToPathAndChanges.containsKey(id) && commitToPathAndChanges.get(id).containsKey(path)
  }

  fun buildPathsMap(): Map<Int, FilePath> {
    val result = ContainerUtil.newHashMap<Int, FilePath>()

    commitToPathAndChanges.forEachEntry { commit, filesToChanges ->
      if (filesToChanges.size == 1) {
        result[commit] = ContainerUtil.getFirstItem(filesToChanges.keys)
      }
      else {
        for ((key, value) in filesToChanges) {
          val changeData = value.values.find { ch -> ch != null && ch.kind != VcsLogPathsIndex.ChangeKind.RENAMED_FROM }
          if (changeData != null) {
            result[commit] = key
            break
          }
        }
      }

      true
    }

    return result
  }

  fun isTrivialMerge(commit: Int, path: FilePath): Boolean {
    if (!commitToPathAndChanges.containsKey(commit)) return false
    val data = commitToPathAndChanges.get(commit)[path]
    // strictly speaking, the criteria for merge triviality is a little bit more tricky than this:
    // some merges have just reverted changes in one of the branches
    // they need to be displayed
    // but we skip them instead
    return data != null && data.size > 1 && data.containsValue(null)
  }

  companion object {
    private val LOG = Logger.getInstance(FileNamesData::class.java)
  }
}
