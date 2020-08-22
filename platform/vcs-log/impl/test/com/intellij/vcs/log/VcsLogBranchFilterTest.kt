// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log

import com.intellij.vcs.log.visible.filters.VcsLogFilterObject.fromBranchPatterns
import org.junit.Assert.assertTrue
import org.junit.Test

class VcsLogBranchFilterTest {

  /**
   * Checks that VcsLogBranchFilter accepts branches matched to patterns.
   * <p>
   * Please note that VcsLogBranchFilter considers branch patterns not included into existing branches by default.
   */
  @Test
  fun testIncludedBranches() {
    val patterns = listOf("master",
                          "John.Doe/my-awesome-proposal",
                          "test")

    val filter = createFilter(patterns, existingBranches = emptySet())

    assertTrue(
      filter.matchesAll(listOf("master",
                               "test")))

    assertTrue(
      filter.notMatchesAll(listOf("trunk")))
  }

  /**
   * Checks that VcsLogBranchFilter accepts only existing branches matched to patterns.
   */
  @Test
  fun testNotMatchedBranchPatterns() {
    val patterns = listOf("master",
                          "John.Doe/my-awesome-proposal",
                          "test")

    val existingBranches = setOf("master",
                                 "John.Doe/my-awesome-proposal")

    val filter = createFilter(patterns, existingBranches, excludeNotMatched = true)

    assertTrue(
      filter.matchesAll(listOf("master",
                               "John.Doe/my-awesome-proposal")))

    assertTrue(
      filter.notMatchesAll(listOf("test")))
  }

  /**
   * Checks that VcsLogBranchFilter does not accept branches added to patterns as excluded (e.g. "-master").
   */
  @Test
  fun testExcludedBranches() {
    val patterns = listOf("-master",
                          "-John.Doe/my-awesome-proposal",
                          "test")

    val filter = createFilter(patterns, existingBranches = emptySet())

    assertTrue(
      filter.notMatchesAll(listOf("master")))

    assertTrue(
      filter.matchesAll(listOf("test")))
  }

  /**
   * Checks that VcsLogBranchFilter accepts branches matched to regular expression patterns.
   * <p>
   * Please note that VcsLogBranchFilter considers regular expression patterns not matched to existing branches by default.
   */
  @Test
  fun testIncludedRegexps() {
    val patterns = listOf("^feature/.*$")

    val filter = createFilter(patterns, existingBranches = emptySet())

    assertTrue(
      filter.matchesAll(listOf("feature/staging",
                               "feature/smart-diff")))

    assertTrue(
      filter.notMatchesAll(listOf("John.Doe/feature")))
  }

  /**
   * Checks that VcsLogBranchFilter accepts branches matched to regular expression patterns that match to existing branches.
   */
  @Test
  fun testNotMatchedRegexpPatterns() {
    val patterns = listOf("^feature/.*$", ".+/feature.+")
    val existingBranches = setOf("feature/staging",
                                 "feature/smart-diff")

    val filter = createFilter(patterns, existingBranches, excludeNotMatched = true)

    assertTrue(
      filter.matchesAll(listOf("feature/staging",
                               "feature/smart-diff")))
  }

  /**
   * Checks that VcsLogBranchFilter does not accept branches not matched to regular expression patterns.
   */
  @Test
  fun testExcludedRegexps() {
    val patterns = listOf("-John.Doe/feature-.+",
                          "feature/.+")

    val filter = createFilter(patterns, existingBranches = emptySet())

    assertTrue(
      filter.notMatchesAll(listOf("John.Doe/feature-staging",
                                  "John.Doe/feature-smart-diff")))

    assertTrue(
      filter.matchesAll(listOf("feature/refactor-rebase")))
  }

  private fun createFilter(patterns: List<String>,
                           existingBranches: Set<String>,
                           excludeNotMatched: Boolean = false) = fromBranchPatterns(patterns, existingBranches, excludeNotMatched)

  private fun VcsLogBranchFilter.matchesAll(branches: Collection<String>) = branches.all { branch -> matches(branch) }

  private fun VcsLogBranchFilter.notMatchesAll(branches: Collection<String>) = !matchesAll(branches)
}