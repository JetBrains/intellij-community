// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible.filters

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.text.CharFilter
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.*
import com.intellij.vcs.log.VcsLogFilterCollection.FilterKey
import com.intellij.vcs.log.VcsLogFilterCollection.HASH_FILTER
import com.intellij.vcs.log.VcsLogRangeFilter.RefRange
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.util.VcsUserUtil
import com.intellij.vcsUtil.VcsUtil
import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet
import org.jetbrains.annotations.Nls
import java.util.*
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

object VcsLogFilterObject {
  private val LOG = Logger.getInstance("#com.intellij.vcs.log.visible.filters.VcsLogFilters")

  const val ME = "*"

  @JvmStatic
  fun fromPattern(text: String, isRegexpAllowed: Boolean = false, isMatchCase: Boolean = false): VcsLogTextFilter {
    if (isRegexpAllowed && VcsLogUtil.isRegexp(text)) {
      try {
        return VcsLogRegexTextFilter(Pattern.compile(text, if (isMatchCase) 0 else Pattern.CASE_INSENSITIVE))
      }
      catch (ignored: PatternSyntaxException) {
      }
    }
    return VcsLogTextFilterImpl(text, isMatchCase)
  }

  @JvmStatic
  fun fromPatternsList(patterns: List<String>, isMatchCase: Boolean = false): VcsLogTextFilter {
    if (patterns.isEmpty()) return fromPattern("", false, isMatchCase)
    if (patterns.size == 1) return fromPattern(patterns.single(), false, isMatchCase)
    return VcsLogMultiplePatternsTextFilter(patterns, isMatchCase)
  }

  @JvmStatic
  fun fromBranch(branchName: String): VcsLogBranchFilter {
    return fromBranches(listOf(branchName))
  }

  @JvmStatic
  fun fromBranches(branchNames: List<String>): VcsLogBranchFilter {
    return VcsLogBranchFilterImpl(branchNames, emptyList(), emptyList(), emptyList())
  }

  @JvmStatic
  fun fromRange(exclusiveRef: String, inclusiveRef: String): VcsLogRangeFilter {
    return fromRange(listOf(RefRange(exclusiveRef, inclusiveRef)))
  }

  @JvmStatic
  fun fromRange(ranges: List<RefRange>): VcsLogRangeFilter {
    return VcsLogRangeFilterImpl(ranges)
  }

  @JvmOverloads
  @JvmStatic
  fun fromBranchPatterns(patterns: Collection<String>,
                         existingBranches: Set<String>,
                         excludeNotMatched: Boolean = false): VcsLogBranchFilter {

    val includedBranches = ArrayList<String>()
    val excludedBranches = ArrayList<String>()
    val includedPatterns = ArrayList<Pattern>()
    val excludedPatterns = ArrayList<Pattern>()
    val shouldExcludeNotMatched = existingBranches.isNotEmpty() && excludeNotMatched

    for (pattern in patterns) {
      val isExcluded = pattern.startsWith("-")
      val string = if (isExcluded) pattern.substring(1) else pattern
      val isRegexp = !existingBranches.contains(string) && VcsLogUtil.isRegexp(string)

      if (isRegexp) {
        try {
          val regex = Pattern.compile(string)
          if (isExcluded) {
            excludedPatterns.add(regex)
          }
          else {
            includedPatterns.add(regex)
          }
        }
        catch (e: PatternSyntaxException) {
          LOG.warn("Pattern $string is not a proper regular expression and no branch can be found with that name.", e)
          if (shouldExcludeNotMatched) {
            continue
          }
          if (isExcluded) {
            excludedBranches.add(string)
          }
          else {
            includedBranches.add(string)
          }
        }
      }
      else {
        if (shouldExcludeNotMatched && !existingBranches.contains(string)) {
          continue
        }
        if (isExcluded) {
          excludedBranches.add(string)
        }
        else {
          includedBranches.add(string)
        }
      }
    }

    return VcsLogBranchFilterImpl(includedBranches, includedPatterns, excludedBranches, excludedPatterns)
  }

  @JvmStatic
  fun fromCommit(commit: CommitId): VcsLogRevisionFilter {
    return VcsLogRevisionFilterImpl(listOf(commit))
  }

  @JvmStatic
  fun fromCommits(commits: List<CommitId>): VcsLogRevisionFilter {
    return VcsLogRevisionFilterImpl(commits)
  }

  @JvmStatic
  fun fromHash(text: String): VcsLogHashFilter? {
    val hashes = mutableListOf<String>()
    for (word in StringUtil.split(text, HashSeparatorCharFilter, true, true)) {
      if (!VcsLogUtil.HASH_REGEX.matcher(word).matches()) {
        return null
      }
      hashes.add(word)
    }
    if (hashes.isEmpty()) return null

    return fromHashes(hashes)
  }

  @JvmStatic
  fun fromHashes(hashes: Collection<String>): VcsLogHashFilter {
    return VcsLogHashFilterImpl(hashes)
  }

  @JvmStatic
  fun fromDates(after: Date?, before: Date?): VcsLogDateFilter {
    return VcsLogDateFilterImpl(after, before)
  }

  @JvmStatic
  fun fromDates(after: Long, before: Long): VcsLogDateFilter {
    return fromDates(if (after > 0) Date(after) else null, if (before > 0) Date(before) else null)
  }

  @JvmStatic
  fun fromUserNames(userNames: Collection<String>, vcsLogData: VcsLogData): VcsLogUserFilter {
    return VcsLogUserFilterImpl(userNames, vcsLogData.userNameResolver)
  }

  @JvmStatic
  fun fromUser(user: VcsUser, allUsers: Set<VcsUser> = setOf(user)): VcsLogUserFilter {
    return fromUserNames(listOf(VcsUserUtil.getShortPresentation(user)), emptyMap(), allUsers)
  }

  @JvmStatic
  fun fromUserNames(userNames: Collection<String>, meData: Map<VirtualFile, VcsUser>, allUsers: Set<VcsUser>): VcsLogUserFilter {
    return VcsLogUserFilterImpl(userNames, meData, allUsers)
  }

  @JvmStatic
  fun fromPaths(files: Collection<FilePath>): VcsLogStructureFilter {
    return VcsLogStructureFilterImpl(files)
  }

  @JvmStatic
  fun fromVirtualFiles(files: Collection<VirtualFile>): VcsLogStructureFilter {
    return fromPaths(files.map { file -> VcsUtil.getFilePath(file) })
  }

  @JvmStatic
  fun fromRoot(root: VirtualFile): VcsLogRootFilter {
    return fromRoots(listOf(root))
  }

  @JvmStatic
  fun fromRoots(roots: Collection<VirtualFile>): VcsLogRootFilter {
    return VcsLogRootFilterImpl(roots)
  }

  @JvmStatic
  fun noMerges(): VcsLogParentFilter {
    return fromParentCount(maxParents = 1)
  }

  @JvmStatic
  fun fromParentCount(minParents: Int? = null, maxParents: Int? = null): VcsLogParentFilter {
    return VcsLogParentFilterImpl(minParents ?: 0, maxParents ?: Int.MAX_VALUE)
  }

  @JvmStatic
  fun collection(vararg filters: VcsLogFilter?): VcsLogFilterCollection {
    val filterSet = createFilterSet()
    for (f in filters) {
      if (f != null && replace(filterSet, f)) {
        LOG.warn("Two filters with the same key ${f.key} in filter collection. Keeping only ${f}.")
      }
    }
    return VcsLogFilterCollectionImpl(filterSet)
  }

  @JvmField
  val EMPTY_COLLECTION = collection()
}

fun VcsLogFilterCollection.with(filter: VcsLogFilter?): VcsLogFilterCollection {
  if (filter == null) return this

  val filterSet = createFilterSet()
  filterSet.addAll(this.filters)
  replace(filterSet, filter)
  return VcsLogFilterCollectionImpl(filterSet)
}

fun VcsLogFilterCollection.without(condition: (VcsLogFilter) -> Boolean): VcsLogFilterCollection {
  val filterSet = createFilterSet()
  this.filters.forEach { if (!(condition(it))) filterSet.add(it) }
  return VcsLogFilterCollectionImpl(filterSet)
}

fun VcsLogFilterCollection.without(filterKey: FilterKey<*>): VcsLogFilterCollection {
  return without { it.key == filterKey }
}

fun <T : VcsLogFilter> VcsLogFilterCollection.without(filterClass: Class<T>): VcsLogFilterCollection {
  return without { filterClass.isInstance(it) }
}

val VcsLogFilterCollection.keysToSet get() = this.filters.mapTo(mutableSetOf()) { it.key }

fun VcsLogFilterCollection.matches(vararg filterKey: FilterKey<*>): Boolean = matches(filterKey.toSet())

fun VcsLogFilterCollection.matches(filterKeys: Set<FilterKey<*>>): Boolean {
  return this.keysToSet == filterKeys
}

@Nls
fun VcsLogFilterCollection.getPresentation(withPrefix: Boolean = false): String {
  if (get(HASH_FILTER) != null) {
    return get(HASH_FILTER)!!.displayText
  }
  return StringUtil.join(filters, { filter: VcsLogFilter ->
    if (filters.size != 1 || withPrefix) {
      filter.withPrefix()
    }
    else filter.displayText
  }, " ")
}

@Nls
private fun VcsLogFilter.withPrefix(): String {
  when (this) {
    is VcsLogTextFilter -> return VcsLogBundle.message("vcs.log.filter.text.presentation.with.prefix", displayText)
    is VcsLogUserFilter -> return VcsLogBundle.message("vcs.log.filter.user.presentation.with.prefix", displayText)
    is VcsLogDateFilter -> return VcsLogDateFilterImpl.getDisplayTextWithPrefix(this)
    is VcsLogBranchFilter -> return VcsLogBundle.message("vcs.log.filter.branch.presentation.with.prefix", displayText)
    is VcsLogRootFilter -> return VcsLogBundle.message("vcs.log.filter.root.presentation.with.prefix", displayText)
    is VcsLogStructureFilter -> return VcsLogBundle.message("vcs.log.filter.structure.presentation.with.prefix", displayText)
  }
  return displayText
}

private fun createFilterSet() = ObjectOpenCustomHashSet(object : Hash.Strategy<VcsLogFilter> {
  override fun hashCode(o: VcsLogFilter?): Int {
    return o?.key?.hashCode() ?: 0
  }

  override fun equals(o1: VcsLogFilter?, o2: VcsLogFilter?): Boolean {
    return o1 === o2 || (o1?.key == o2?.key)
  }
})

private fun <T> replace(set: ObjectOpenCustomHashSet<T>, element: T): Boolean {
  val isModified = set.remove(element)
  set.add(element)
  return isModified
}

internal object HashSeparatorCharFilter : CharFilter {
  override fun accept(ch: Char): Boolean {
    if (ch == ',' || ch == ';') return true
    if (Character.isWhitespace(ch)) return true
    return false
  }

  @JvmStatic
  fun invert(): CharFilter {
    return object : CharFilter {
      override fun accept(ch: Char): Boolean {
        return !HashSeparatorCharFilter.accept(ch)
      }
    }
  }
}
