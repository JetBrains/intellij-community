// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.util.BeforeAfter
import com.intellij.util.ThreeState
import java.util.Collections

private val LOG = logger<ChangeListsIndexes>()

internal abstract class ChangeListsIndexes protected constructor(
  private val dataMap: Map<FilePath, Data>,
  private val changes: Set<Change>,
) {
  fun getChanges(): Set<Change> = changes

  fun getAffectedPaths(): Set<FilePath> = dataMap.keys

  fun getChange(file: FilePath): Change? = dataMap[file]?.change

  fun getStatus(file: FilePath): FileStatus? = dataMap[file]?.status

  fun getVcsFor(change: Change): AbstractVcs? {
    return getVcsForRevision(change.afterRevision) ?: getVcsForRevision(change.beforeRevision)
  }

  private fun getVcsForRevision(revision: ContentRevision?): AbstractVcs? {
    if (revision == null) return null
    return dataMap[revision.file]?.vcs
  }

  /**
   * Collects every path that changed between this index and [newIndexes].
   *
   * A local changes refresh calls this method. It collects:
   * - a path that is new in the local changes
   * - a path that is no longer changed locally
   * - a path that stays changed, but got a new base revision, for example after an external update
   *
   * [RemoteRevisionsCache] and the annotation listener consume the result.
   */
  fun getDelta(
    newIndexes: ChangeListsIndexes,
    toRemove: MutableSet<in BaseRevision>,
    toAdd: MutableSet<in BaseRevision>,
    toModify: MutableSet<in BeforeAfter<BaseRevision>>,
  ) {
    val oldMap = dataMap
    val newMap = newIndexes.dataMap

    for ((path, oldData) in oldMap) {
      val newData = newMap[path]

      if (newData != null) {
        if (!oldData.sameRevisions(newData)) {
          toModify.add(BeforeAfter(createBaseRevision(path, oldData), createBaseRevision(path, newData)))
        }
      }
      else {
        toRemove.add(createBaseRevision(path, oldData))
      }
    }

    for ((path, newData) in newMap) {
      if (!oldMap.containsKey(path)) {
        toAdd.add(createBaseRevision(path, newData))
      }
    }
  }

  private fun createBaseRevision(path: FilePath, data: Data): BaseRevision = BaseRevision(data.vcs, data.revision, path)

  fun toMutable(): Mutable = Mutable(this)

  fun toIndexed(): Indexed = Indexed(this)

  /**
   * Immutable thread-safe index where changes are indexed with [affectedPathSet] to allow optimized [haveChangesUnder] queries
   */
  class Indexed private constructor(
    dataMap: Map<FilePath, Data>,
    changes: Set<Change>,
  ) : ChangeListsIndexes(dataMap, changes) {
    constructor() : this(mapOf(), setOf())

    constructor(other: ChangeListsIndexes) : this(
      Collections.unmodifiableMap(HashMap(other.dataMap)),
      Collections.unmodifiableSet(HashSet(other.changes)),
    )

    private val affectedPathSet = AffectedPathSet(dataMap.keys)

    /**
     * Reports whether a changed file exists under [path].
     *
     * [ThreeState.NO] - there are no changed files under this directory
     * [ThreeState.YES] - there are modified direct children of this directory
     * [ThreeState.UNSURE] - there are modified non-direct children of this directory
     */
    fun haveChangesUnder(path: FilePath): ThreeState = affectedPathSet.haveChangesUnder(path)
  }

  class Mutable private constructor(
    private val dataMap: MutableMap<FilePath, Data>,
    private val changes: MutableSet<Change>,
  ) : ChangeListsIndexes(dataMap, changes) {
    constructor() : this(HashMap(), HashSet())

    constructor(other: ChangeListsIndexes) : this(HashMap(other.dataMap), HashSet(other.changes))

    fun changeAdded(change: Change, key: AbstractVcs?) {
      changes.add(change)

      val afterRevision = change.afterRevision
      val beforeRevision = change.beforeRevision

      if (beforeRevision != null && afterRevision != null) {
        add(afterRevision.file, change, change.fileStatus, key, beforeRevision.revisionNumber)

        if (beforeRevision.file != afterRevision.file) {
          add(beforeRevision.file, change, FileStatus.DELETED, key, beforeRevision.revisionNumber)
        }
      }
      else if (afterRevision != null) {
        add(afterRevision.file, change, change.fileStatus, key, VcsRevisionNumber.NULL)
      }
      else if (beforeRevision != null) {
        add(beforeRevision.file, change, change.fileStatus, key, beforeRevision.revisionNumber)
      }
    }

    fun changeRemoved(change: Change) {
      val wasRemoved = changes.remove(change)
      if (LOG.isDebugEnabled && !wasRemoved) {
        LOG.debug("Change wasn't removed: $change")
      }

      val afterRevision = change.afterRevision
      val beforeRevision = change.beforeRevision

      if (afterRevision != null) {
        remove(afterRevision.file)
      }
      if (beforeRevision != null) {
        remove(beforeRevision.file)
      }
    }

    private fun add(file: FilePath, change: Change, status: FileStatus, key: AbstractVcs?, number: VcsRevisionNumber) {
      dataMap[file] = Data(status, change, key, number)
      LOG.debug { "Set status $status for $file" }
    }

    private fun remove(file: FilePath) {
      dataMap.remove(file)
      LOG.debug { "Clear status for $file" }
    }
  }

  protected class Data(
    val status: FileStatus,
    val change: Change,
    val vcs: AbstractVcs?,
    val revision: VcsRevisionNumber,
  ) {
    fun sameRevisions(data: Data): Boolean = vcs == data.vcs && revision == data.revision
  }

  companion object {
    /** An index with no changed path. [ChangeListWorker.Main] starts from it. */
    @JvmField
    val EMPTY: Indexed = Indexed()
  }
}
