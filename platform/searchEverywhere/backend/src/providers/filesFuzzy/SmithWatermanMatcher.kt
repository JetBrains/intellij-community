// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.filesFuzzy

import com.intellij.ide.actions.searcheverywhere.fuzzyMatching.ScoringParameters
import com.intellij.ide.actions.searcheverywhere.fuzzyMatching.SmithWatermanAlgorithm
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.fuzzyMatching.FuzzyMatchResult
import com.intellij.util.fuzzyMatching.GotoFileFuzzyMatcher
import org.jetbrains.annotations.ApiStatus

/**
 * Adapter that wraps SmithWatermanAlgorithm for use in file search.
 * Provides caching and file-specific scoring adjustments.
 *
 * Caching is done per search session to avoid recomputing scores for
 * the same files when the user types additional characters.
 */
@ApiStatus.Internal
class SmithWatermanMatcher(private val pattern: String) : GotoFileFuzzyMatcher {
  private val params = ScoringParameters()
  private val cache = mutableMapOf<String, FuzzyMatchResult>()
  private val extension = pattern.substringAfterLast('.', "")
  private val normalizedPattern = if (extension.isNotEmpty()) pattern.substringBeforeLast('.') else pattern

  /**
   * Matches the pattern against the file name only.
   *
   * @param file Virtual file to match against
   * @return Match result with score and matched indices
   */
  override fun match(file: VirtualFile): FuzzyMatchResult {
    val fileName = file.name
    return match(fileName)
  }

  override fun match(fileName: String): FuzzyMatchResult {
    return cache.getOrPut(fileName) {
      val fileExtension = fileName.substringAfterLast('.', "")
      if (extension.isNotEmpty() && fileExtension != extension)
        return@getOrPut FuzzyMatchResult.NO_MATCH

      val normalizedFileName = if (extension.isNotEmpty()) fileName.substringBeforeLast('.') else fileName

      SmithWatermanAlgorithm.match(normalizedPattern, normalizedFileName, params)
    }
  }

  /**
   * Matches the pattern against the full file path, with filename-first strategy.
   *
   * If the filename match has a high score (>0.7 normalized), we use that.
   * Otherwise, we try matching against the full path for cases where the
   * user is searching for path components (e.g., "src/main").
   *
   * @param file Virtual file to match against
   * @return Match result with score and matched indices
   */
  override fun matchWithPath(file: VirtualFile): FuzzyMatchResult {
    val fullPath = file.path
    return cache.getOrPut(fullPath) {
      // Try filename first
      val fileNameResult = match(file)
      if (fileNameResult.normalizedScore > 0.7) {
        fileNameResult
      } else {
        // Try full path if filename match is weak
        SmithWatermanAlgorithm.match(pattern, fullPath, params)
      }
    }
  }

  override fun dispose() {
    cache.clear()
  }
}
