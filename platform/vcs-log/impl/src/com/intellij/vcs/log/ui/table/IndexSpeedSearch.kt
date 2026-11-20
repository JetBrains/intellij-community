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
import com.intellij.vcs.log.data.index.IndexDataGetter
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
import javax.swing.event.TableModelEvent

internal open class IndexSpeedSearch(project: Project, private val index: VcsLogIndex, private val storage: VcsLogStorage, component: VcsLogGraphTable) :
  VcsLogSpeedSearch(component) {

  private val userRegistry: VcsUserRegistry = project.getService(VcsUserRegistry::class.java)

  /** Counts row changes of the table model, including graph changes within the same visible pack (expanding linear branches). */
  private var rowsVersion: Int = 0

  private val asyncSearch: VcsLogAsyncRowSearch<SearchSnapshot> = VcsLogAsyncRowSearch(
    scope = project.service<VcsLogSpeedSearchScope>().cs.childScope("Vcs log speed search").also { it.coroutineContext.job.cancelOnDispose(component) },
    rows = AsyncRows(),
    uiContext = Dispatchers.EDT + ModalityState.any().asContextElement(),
  )

  init {
    // updates of single columns (root column on scrolling, external columns) do not change rows or metadata columns
    myComponent.model.addTableModelListener { e -> if (e.column == TableModelEvent.ALL_COLUMNS) rowsVersion++ }
    addChangeListener { evt: PropertyChangeEvent ->
      if (evt.propertyName == ENTERED_PREFIX_PROPERTY_NAME && (evt.newValue as? String).isNullOrEmpty()) {
        asyncSearch.reset()
      }
    }
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

  override fun findFirstElement(s: String): Any? {
    if (index.dataGetter == null) return super.findFirstElement(s)
    return findAsync(s, Direction.FIRST)
  }

  override fun findLastElement(s: String): Any? {
    if (index.dataGetter == null) return super.findLastElement(s)
    return findAsync(s, Direction.LAST)
  }

  private fun findAsync(s: String, direction: Direction): Int? {
    val pattern = s.trim()
    // highlighting takes the pattern from the recent search text of the UI comparator,
    // which is not set by the search when no row is in memory
    comparator.matchingFragments(pattern, "")
    return asyncSearch.find(pattern, selectedIndex, direction, UISettings.getInstance().cycleScrolling)
  }

  override fun getCommitMetadata(row: VcsLogTableIndex): VcsCommitMetadata? {
    val commitId = getCommitId(row) ?: return null
    return getCachedCommitMetadataById(commitId) ?: super.getCommitMetadata(row)
  }

  /**
   * In-memory metadata besides the table model cache. Called on EDT and in the background.
   */
  protected open fun getCachedCommitMetadataById(commitId: VcsLogCommitStorageIndex): VcsCommitMetadata? = null

  protected fun getCommitId(row: VcsLogTableIndex): VcsLogCommitStorageIndex? = myComponent.model.getId(row)

  private class SearchSnapshot(
    @JvmField val model: GraphTableModel,
    @JvmField val columns: List<VcsLogMetadataColumn>,
    @JvmField val dataGetter: IndexDataGetter?,
    @JvmField val comparator: SpeedSearchComparator,
    @JvmField val commitsForUsers: IntSet = IntSets.emptySet(),
  )

  private inner class AsyncRows : VcsLogAsyncRowSearch.Rows<SearchSnapshot> {
    override fun getRowCount(): Int = elementCount

    override fun getSelectedRow(): Int = selectedIndex

    override fun getCommitIdAt(row: Int): VcsLogCommitStorageIndex? = getCommitId(row)

    override fun getContextKey(): Any = Triple(rowsVersion, myComponent.model.visiblePack, columnsForSpeedSearch)

    override fun matchesInMemory(row: Int, pattern: String): Boolean? {
      if (getCommitId(row) == null) return false
      // the table model cache, does not load anything
      val metadata = getCommitMetadata(row) ?: return null
      // authors always match, even with the Author column hidden, like the users index in "prepare"
      val columns = columnsForSpeedSearch.let { if (Author in it) it else it + Author }
      return isMatchingMetadata(pattern, metadata, columns)
    }

    override fun createSnapshot(pattern: String): SearchSnapshot {
      return SearchSnapshot(myComponent.model, columnsForSpeedSearch, index.dataGetter,
                            // SpeedSearchComparator caches its matcher and is not thread-safe, so the search gets its own one
                            SpeedSearchComparator(false))
    }

    override suspend fun prepare(pattern: String, snapshot: SearchSnapshot): SearchSnapshot {
      val dataGetter = snapshot.dataGetter ?: return snapshot
      // authors are matched by the users index, it is faster than reading the author of each commit
      val users = userRegistry.users.filter { snapshot.comparator.matchingFragments(pattern, VcsUserUtil.getShortPresentation(it)) != null }
      if (users.isEmpty()) return snapshot
      val commitsForUsers = dataGetter.filter(listOf<VcsLogDetailsFilter>(SimpleVcsLogUserFilter(users)))
      return SearchSnapshot(snapshot.model, snapshot.columns, dataGetter, snapshot.comparator, commitsForUsers)
    }

    override suspend fun matches(commitId: VcsLogCommitStorageIndex, pattern: String, snapshot: SearchSnapshot): Boolean {
      if (snapshot.commitsForUsers.contains(commitId)) return true
      val metadata = getCachedCommitMetadataById(commitId)
                     ?: snapshot.dataGetter?.let { IndexedDetails(it, storage, commitId) }
                     ?: return false
      // Author column is excluded for indexed details since authors are matched in "prepare"
      val columns = if (metadata is IndexedDetails) snapshot.columns - Author else snapshot.columns
      return columns.any { snapshot.comparator.matchingFragments(pattern, it.getValue(snapshot.model, metadata)) != null }
    }

    override fun getCurrentPattern(): String? = enteredPrefix?.trim()

    override fun onFound(row: Int, pattern: String) {
      selectElement(row, pattern)
      refreshSelection()
    }
  }
}

@Service(Service.Level.PROJECT)
internal class VcsLogSpeedSearchScope(@JvmField val cs: CoroutineScope)

private class SimpleVcsLogUserFilter(private val users: Collection<VcsUser>) : VcsLogUserFilter {
  override fun getUsers(root: VirtualFile): Collection<VcsUser> = users
  override fun getValuesAsText(): Collection<String> = users.map { VcsUserUtil.toExactString(it) }
  override fun matches(details: VcsCommitMetadata): Boolean = users.contains(details.author)
}
