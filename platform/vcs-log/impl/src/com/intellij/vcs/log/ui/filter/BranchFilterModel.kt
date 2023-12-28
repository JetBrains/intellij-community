// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.filter

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import java.util.function.Supplier
import java.util.regex.Pattern

class BranchFilterModel internal constructor(private val dataPackProvider: Supplier<out VcsLogDataPack>,
                                             private val storage: VcsLogStorage,
                                             private val roots: Collection<VirtualFile>,
                                             properties: MainVcsLogUiProperties,
                                             filters: VcsLogFilterCollection?) : FilterModel<BranchFilters>(properties) {
  var visibleRoots: Collection<VirtualFile>? = null
    private set

  init {
    if (filters != null) {
      saveFilterToProperties(BranchFilters(filters.get(VcsLogFilterCollection.BRANCH_FILTER),
                                           filters.get(VcsLogFilterCollection.REVISION_FILTER),
                                           filters.get(VcsLogFilterCollection.RANGE_FILTER)))
    }
  }

  override fun setFilter(filter: BranchFilters?) {
    var newFilters = filter
    if (newFilters != null && newFilters.isEmpty()) newFilters = null

    var anyFilterDiffers = false

    if (newFilters?.branchFilter != _filter?.branchFilter) {
      if (newFilters?.branchFilter != null) VcsLogUsageTriggerCollector.triggerFilterSet(VcsLogFilterCollection.BRANCH_FILTER.name)
      anyFilterDiffers = true
    }
    if (newFilters?.revisionFilter != _filter?.revisionFilter) {
      if (newFilters?.revisionFilter != null) VcsLogUsageTriggerCollector.triggerFilterSet(VcsLogFilterCollection.REVISION_FILTER.name)
      anyFilterDiffers = true
    }
    if (newFilters?.rangeFilter != _filter?.rangeFilter) {
      if (newFilters?.rangeFilter != null) VcsLogUsageTriggerCollector.triggerFilterSet(VcsLogFilterCollection.RANGE_FILTER.name)
      anyFilterDiffers = true
    }
    if (anyFilterDiffers) {
      super.setFilter(newFilters)
    }
  }

  override fun saveFilterToProperties(filter: BranchFilters?) {
    uiProperties.saveFilterValues(VcsLogFilterCollection.BRANCH_FILTER.name, filter?.branchFilter?.let { getBranchFilterValues(it) })
    uiProperties.saveFilterValues(VcsLogFilterCollection.REVISION_FILTER.name,
                                  filter?.revisionFilter?.let { getRevisionFilterValues(it) })
    uiProperties.saveFilterValues(VcsLogFilterCollection.RANGE_FILTER.name, filter?.rangeFilter?.let { getRangeFilterValues(it) })
  }

  override fun getFilterFromProperties(): BranchFilters? {
    val branchFilterValues = uiProperties.getFilterValues(VcsLogFilterCollection.BRANCH_FILTER.name)
    val branchFilter = branchFilterValues?.let { createBranchFilter(it) }

    val revisionFilterValues = uiProperties.getFilterValues(VcsLogFilterCollection.REVISION_FILTER.name)
    val revisionFilter = revisionFilterValues?.let { createRevisionFilter(it) }

    val rangeFilterValues = uiProperties.getFilterValues(VcsLogFilterCollection.RANGE_FILTER.name)
    val rangeFilter = rangeFilterValues?.let { createRangeFilter(it) }

    if (branchFilter == null && revisionFilter == null && rangeFilter == null) return null
    return BranchFilters(branchFilter, revisionFilter, rangeFilter)
  }

  fun onStructureFilterChanged(rootFilter: VcsLogRootFilter?, structureFilter: VcsLogStructureFilter?) {
    if (rootFilter == null && structureFilter == null) {
      visibleRoots = null
    }
    else {
      visibleRoots = VcsLogUtil.getAllVisibleRoots(roots, rootFilter, structureFilter)
    }
  }

  val dataPack: VcsLogDataPack
    get() = dataPackProvider.get()

  private fun createBranchFilter(values: List<String>): VcsLogBranchFilter {
    return VcsLogFilterObject.fromBranchPatterns(values, dataPack.refs.branches.mapTo(mutableSetOf()) { it.name })
  }

  private fun createRevisionFilter(values: List<String>): VcsLogRevisionFilter {
    val pattern = Pattern.compile("\\[(.*)\\](" + VcsLogUtil.HASH_REGEX.pattern() + ")")
    return VcsLogFilterObject.fromCommits(values.mapNotNull { s: String ->
      val matcher = pattern.matcher(s)
      if (!matcher.matches()) {
        if (VcsLogUtil.isFullHash(s)) {
          val commitId = findCommitId(HashImpl.build(s))
          if (commitId != null) return@mapNotNull commitId
        }
        LOG.warn("Could not parse '$s' while creating revision filter")
        return@mapNotNull null
      }
      val result = matcher.toMatchResult()
      val root = LocalFileSystem.getInstance().findFileByPath(result.group(1))
      if (root == null) {
        LOG.warn("Root '" + result.group(1) + "' does not exist")
        return@mapNotNull null
      }
      else if (!roots.contains(root)) {
        LOG.warn("Root '" + result.group(1) + "' is not registered")
        return@mapNotNull null
      }
      CommitId(HashImpl.build(result.group(2)), root)
    })
  }

  private fun findCommitId(hash: Hash): CommitId? {
    for (root in roots) {
      val commitId = CommitId(hash, root)
      if (storage.containsCommit(commitId)) {
        return commitId
      }
    }
    return null
  }

  fun createFilterFromPresentation(values: List<String>): BranchFilters {
    val hashes = mutableListOf<String>()
    val branches = mutableListOf<String>()
    val ranges = mutableListOf<String>()
    for (s in values) {
      val twoDots = s.indexOf("..")
      if (twoDots > 0 && twoDots == s.lastIndexOf("..")) {
        ranges.add(s)
      }
      else if (VcsLogUtil.isFullHash(s)) {
        hashes.add(s)
      }
      else {
        branches.add(s)
      }
    }
    val branchFilter = if (branches.isEmpty()) null else createBranchFilter(branches)
    val hashFilter = if (hashes.isEmpty()) null else createRevisionFilter(hashes)
    val refDiffFilter = if (ranges.isEmpty()) null else createRangeFilter(ranges)
    return BranchFilters(branchFilter, hashFilter, refDiffFilter)
  }

  var branchFilter: VcsLogBranchFilter?
    get() = getFilter()?.branchFilter
    set(branchFilter) {
      setFilter(BranchFilters(branchFilter, null, null))
    }

  val revisionFilter: VcsLogRevisionFilter?
    get() = getFilter()?.revisionFilter

  var rangeFilter: VcsLogRangeFilter?
    get() = getFilter()?.rangeFilter
    set(rangeFilter) {
      setFilter(BranchFilters(null, null, rangeFilter))
    }

  companion object {
    private val LOG = Logger.getInstance(BranchFilterModel::class.java)
    private const val TWO_DOTS = ".."

    private fun createRangeFilter(values: List<String>): VcsLogRangeFilter? {
      val ranges = values.mapNotNull { value: String ->
        val twoDots = value.indexOf(TWO_DOTS)
        if (twoDots <= 0) {
          LOG.error("Incorrect range filter value: $values")
          return@mapNotNull null
        }
        VcsLogRangeFilter.RefRange(value.substring(0, twoDots), value.substring(twoDots + TWO_DOTS.length))
      }
      if (ranges.isEmpty()) return null
      return VcsLogFilterObject.fromRange(ranges)
    }

    private fun getBranchFilterValues(filter: VcsLogBranchFilter): List<String> {
      return ArrayList(filter.textPresentation.sorted())
    }

    private fun getRevisionFilterValues(filter: VcsLogRevisionFilter): List<String> {
      return filter.heads.map { id -> "[" + id.root.path + "]" + id.hash.asString() }
    }

    private fun getRangeFilterValues(rangeFilter: VcsLogRangeFilter): List<String> {
      return ArrayList(rangeFilter.getTextPresentation())
    }

    private fun getRevisionFilter2Presentation(filter: VcsLogRevisionFilter): List<String> {
      return filter.heads.map { id -> id.hash.asString() }
    }

    @JvmStatic
    fun getFilterPresentation(filters: BranchFilters): List<String> {
      val branchFilterValues = filters.branchFilter?.let { getBranchFilterValues(it) } ?: emptyList()
      val revisionFilterValues = filters.revisionFilter?.let { getRevisionFilter2Presentation(it) } ?: emptyList()
      val rangeFilterValues = filters.rangeFilter?.let { getRangeFilterValues(filters.rangeFilter) } ?: emptyList()
      return branchFilterValues + revisionFilterValues + rangeFilterValues
    }
  }
}