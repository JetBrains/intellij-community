// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.filter

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import java.util.function.Supplier
import java.util.regex.Pattern

class BranchFilterModel internal constructor(private val dataPackProvider: Supplier<out VcsLogDataPack>,
                                             private val storage: VcsLogStorage,
                                             private val roots: Collection<VirtualFile>,
                                             properties: MainVcsLogUiProperties,
                                             filters: VcsLogFilterCollection?) :
  FilterModel.MultipleFilterModel(listOf(VcsLogFilterCollection.BRANCH_FILTER, VcsLogFilterCollection.REVISION_FILTER,
                                         VcsLogFilterCollection.RANGE_FILTER), properties, filters) {

  var visibleRoots: Collection<VirtualFile>? = null
    internal set

  override fun createFilter(key: VcsLogFilterCollection.FilterKey<*>, values: List<String>): VcsLogFilter? {
    return when (key) {
      VcsLogFilterCollection.BRANCH_FILTER -> createBranchFilter(values)
      VcsLogFilterCollection.REVISION_FILTER -> createRevisionFilter(values)
      VcsLogFilterCollection.RANGE_FILTER -> createRangeFilter(values)
      else -> null
    }
  }

  override fun getFilterValues(filter: VcsLogFilter): List<String>? {
    return when (filter) {
      is VcsLogBranchFilter -> getBranchFilterValues(filter)
      is VcsLogRevisionFilter -> getRevisionFilterValues(filter)
      is VcsLogRangeFilter -> getRangeFilterValues(filter)
      else -> null
    }
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

  fun createFilterFromPresentation(values: List<String>): VcsLogFilterCollection {
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
    return VcsLogFilterObject.collection(branchFilter, hashFilter, refDiffFilter)
  }

  var branchFilter by filterProperty(VcsLogFilterCollection.BRANCH_FILTER)
  var revisionFilter by filterProperty(VcsLogFilterCollection.REVISION_FILTER)
  var rangeFilter by filterProperty(VcsLogFilterCollection.RANGE_FILTER)

  companion object {
    private val LOG = Logger.getInstance(BranchFilterModel::class.java)
    private const val TWO_DOTS = ".."

    internal val branchFilterKeys = setOf(VcsLogFilterCollection.BRANCH_FILTER, VcsLogFilterCollection.REVISION_FILTER,
                                          VcsLogFilterCollection.RANGE_FILTER)

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
    fun getFilterPresentation(filters: VcsLogFilterCollection): List<String> {
      val branchFilterValues = filters[VcsLogFilterCollection.BRANCH_FILTER]?.let { getBranchFilterValues(it) } ?: emptyList()
      val revisionFilterValues = filters[VcsLogFilterCollection.REVISION_FILTER]?.let { getRevisionFilter2Presentation(it) }
                                 ?: emptyList()
      val rangeFilterValues = filters[VcsLogFilterCollection.RANGE_FILTER]?.let { getRangeFilterValues(it) } ?: emptyList()
      return branchFilterValues + revisionFilterValues + rangeFilterValues
    }
  }
}