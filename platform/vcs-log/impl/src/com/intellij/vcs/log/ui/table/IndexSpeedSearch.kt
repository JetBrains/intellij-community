// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.data.index.IndexedDetails
import com.intellij.vcs.log.data.index.VcsLogIndex
import com.intellij.vcs.log.ui.table.column.Author
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.util.VcsUserUtil
import it.unimi.dsi.fastutil.ints.IntSet
import it.unimi.dsi.fastutil.ints.IntSets
import java.beans.PropertyChangeEvent

internal open class IndexSpeedSearch(project: Project, private val index: VcsLogIndex, private val storage: VcsLogStorage, component: VcsLogGraphTable) :
  VcsLogSpeedSearch(component) {

  private val userRegistry: VcsUserRegistry = project.getService(VcsUserRegistry::class.java)
  private var matchResult: MatchResult? = null

  init {
    addChangeListener { evt: PropertyChangeEvent ->
      if (evt.propertyName == ENTERED_PREFIX_PROPERTY_NAME) {
        matchResult = matchUsers(matchResult, evt.newValue as? String)
      }
    }
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

  override fun isMatchingElement(row: Any, pattern: String): Boolean {
    val commitId = getCommitId(row as Int) ?: return false
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

  override fun getCommitMetadata(row: Int): VcsCommitMetadata? {
    val dataGetter = index.dataGetter ?: return super.getCommitMetadata(row)
    val commitId = getCommitId(row) ?: return null
    return IndexedDetails(dataGetter, storage, commitId)
  }

  protected fun getCommitId(row: Int): Int? = myComponent.model.getId(row)
}

private data class MatchResult(@JvmField val pattern: String,
                               @JvmField val commitsForUsers: IntSet = IntSets.emptySet(),
                               @JvmField val matchingUsers: Collection<VcsUser> = emptySet())

private class SimpleVcsLogUserFilter(private val users: Collection<VcsUser>) : VcsLogUserFilter {
  override fun getUsers(root: VirtualFile): Collection<VcsUser> = users
  override fun getValuesAsText(): Collection<String> = users.map { VcsUserUtil.toExactString(it) }
  override fun matches(details: VcsCommitMetadata): Boolean = users.contains(details.author)
}