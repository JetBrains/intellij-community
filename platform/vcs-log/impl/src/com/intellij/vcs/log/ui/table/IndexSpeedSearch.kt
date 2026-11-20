// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.SpeedSearchComparator
import com.intellij.util.cancelOnDispose
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogCommitStorageIndex
import com.intellij.vcs.log.VcsLogDetailsFilter
import com.intellij.vcs.log.VcsLogUserFilter
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.VcsUserRegistry
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.data.index.IndexedDetails
import com.intellij.vcs.log.data.index.VcsLogIndex
import com.intellij.vcs.log.ui.table.VcsLogAsyncRowSearch.Direction
import com.intellij.vcs.log.ui.table.column.Author
import com.intellij.vcs.log.ui.table.column.VcsLogMetadataColumn
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.util.VcsUserUtil
import it.unimi.dsi.fastutil.ints.IntSet
import it.unimi.dsi.fastutil.ints.IntSets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import java.beans.PropertyChangeEvent

internal open class IndexSpeedSearch(project: Project, private val index: VcsLogIndex, private val storage: VcsLogStorage, component: VcsLogGraphTable) :
  VcsLogSpeedSearch(component) {

  private val userRegistry: VcsUserRegistry = project.getService(VcsUserRegistry::class.java)
  private var matchResult: MatchResult? = null
  private var usersPattern: String? = null

  private val asyncSearch: VcsLogAsyncRowSearch<SearchSnapshot> = VcsLogAsyncRowSearch(
    scope = project.service<VcsLogSpeedSearchScope>().cs.childScope("Vcs log speed search").also { it.coroutineContext.job.cancelOnDispose(component) },
    rows = AsyncRows(),
    uiContext = Dispatchers.EDT + ModalityState.any().asContextElement(),
  )

  init {
    addChangeListener { evt: PropertyChangeEvent ->
      if (evt.propertyName == ENTERED_PREFIX_PROPERTY_NAME) {
        val newPattern = evt.newValue as? String
        updateUsers(newPattern)
        if (newPattern.isNullOrEmpty()) asyncSearch.reset()
      }
    }
  }

  private fun updateUsers(pattern: String?) {
    if (pattern == usersPattern) return
    matchResult = matchUsers(matchResult, pattern)
    usersPattern = pattern
  }

  private fun matchUsers(oldMatchResult: MatchResult?, newPattern: String?): MatchResult? {
    val dataGetter = index.dataGetter
    if (newPattern.isNullOrEmpty() || dataGetter == null) {
      return null
    }

    val oldPattern = oldMatchResult?.pattern
    val usersToExamine = if (oldPattern != null && newPattern.contains(oldPattern)) oldMatchResult.matchingUsers else userRegistry.users
    val matchedUsers = usersToExamine.filter { user -> compare(VcsUserUtil.getShortPresentation(user), newPattern) }
    if (matchedUsers.isEmpty()) return null

    val matchedByUserCommits = dataGetter.filter(listOf<VcsLogDetailsFilter>(SimpleVcsLogUserFilter(matchedUsers)))

    return MatchResult(pattern = newPattern, commitsForUsers = matchedByUserCommits, matchingUsers = matchedUsers)
  }

  override fun isSpeedSearchEnabled(): Boolean {
    if (super.isSpeedSearchEnabled()) {
      val visiblePack = myComponent.model.visiblePack
      return VcsLogUtil.getAllVisibleRoots(visiblePack.logProviders.keys, visiblePack.filters).all { index.isIndexed(it) }
    }
    return false
  }

  override fun findElement(s: String): Any? {
    if (index.dataGetter == null) return super.findElement(s)
    return findAsync(s, Direction.FIND)
  }

  override fun findNextElement(s: String): Any? {
    if (index.dataGetter == null) return super.findNextElement(s)
    return findAsync(s, Direction.NEXT)
  }

  override fun findPreviousElement(s: String): Any? {
    if (index.dataGetter == null) return super.findPreviousElement(s)
    return findAsync(s, Direction.PREVIOUS)
  }

  private fun findAsync(s: String, direction: Direction): Int? {
    // the prefix listener runs after the first search for a new prefix, so match users here to search with the right ones
    updateUsers(s)
    return asyncSearch.find(s.trim(), selectedIndex, direction, UISettings.getInstance().cycleScrolling)
  }

  override fun isMatchingElement(row: Any, pattern: String): Boolean {
    val commitId = getCommitId(row as VcsLogTableIndex) ?: return false
    if (super.isMatchingElement(row, pattern)) return true
    return matchResult?.run {
      commitsForUsers.isNotEmpty() &&  // getting id from row takes time, so optimizing a little here
      commitsForUsers.contains(commitId)
    } ?: false
  }

  override fun isMatchingMetadata(pattern: String, metadata: VcsCommitMetadata?): Boolean {
    if (metadata is IndexedDetails) {
      // Author column is excluded here since authors are filtered separately in "matchUsers" due to performance reasons
      return isMatchingMetadata(pattern, metadata, columnsForSpeedSearch - Author)
    }
    return super.isMatchingMetadata(pattern, metadata)
  }

  override fun getCommitMetadata(row: VcsLogTableIndex): VcsCommitMetadata? {
    val commitId = getCommitId(row) ?: return null
    return getCommitMetadataById(commitId) ?: super.getCommitMetadata(row)
  }

  /**
   * Called on EDT and in the background.
   * @return null when there is nothing better than the table model metadata
   */
  protected open fun getCommitMetadataById(commitId: VcsLogCommitStorageIndex): VcsCommitMetadata? {
    val dataGetter = index.dataGetter ?: return null
    return IndexedDetails(dataGetter, storage, commitId)
  }

  protected fun getCommitId(row: VcsLogTableIndex): VcsLogCommitStorageIndex? = myComponent.model.getId(row)

  private class SearchSnapshot(
    @JvmField val model: GraphTableModel,
    @JvmField val columns: List<VcsLogMetadataColumn>,
    @JvmField val commitsForUsers: IntSet,
    @JvmField val comparator: SpeedSearchComparator,
  )

  private inner class AsyncRows : VcsLogAsyncRowSearch.Rows<SearchSnapshot> {
    override fun rowCount(): Int = elementCount

    override fun commitIdAt(row: Int): VcsLogCommitStorageIndex? = getCommitId(row)

    override fun contextKey(): Any = Pair(myComponent.model.visiblePack, columnsForSpeedSearch)

    override fun snapshot(pattern: String): SearchSnapshot {
      return SearchSnapshot(myComponent.model, columnsForSpeedSearch, matchResult?.commitsForUsers ?: IntSets.emptySet(),
                            // SpeedSearchComparator caches its matcher and is not thread-safe, so the search gets its own one
                            SpeedSearchComparator(false))
    }

    override fun matches(commitId: VcsLogCommitStorageIndex, pattern: String, snapshot: SearchSnapshot): Boolean {
      val metadata = getCommitMetadataById(commitId)
      if (metadata != null) {
        // Author column is excluded for indexed details since authors are matched separately in "matchUsers"
        val columns = if (metadata is IndexedDetails) snapshot.columns - Author else snapshot.columns
        if (columns.any { snapshot.comparator.matchingFragments(pattern, it.getValue(snapshot.model, metadata)) != null }) return true
      }
      return snapshot.commitsForUsers.contains(commitId)
    }

    override fun currentPattern(): String? = enteredPrefix?.trim()

    override fun onFound(row: Int, pattern: String) {
      selectElement(row, pattern)
      refreshSelection()
    }
  }
}

@Service(Service.Level.PROJECT)
internal class VcsLogSpeedSearchScope(@JvmField val cs: CoroutineScope)

private data class MatchResult(@JvmField val pattern: String,
                               @JvmField val commitsForUsers: IntSet = IntSets.emptySet(),
                               @JvmField val matchingUsers: Collection<VcsUser> = emptySet())

private class SimpleVcsLogUserFilter(private val users: Collection<VcsUser>) : VcsLogUserFilter {
  override fun getUsers(root: VirtualFile): Collection<VcsUser> = users
  override fun getValuesAsText(): Collection<String> = users.map { VcsUserUtil.toExactString(it) }
  override fun matches(details: VcsCommitMetadata): Boolean = users.contains(details.author)
}
