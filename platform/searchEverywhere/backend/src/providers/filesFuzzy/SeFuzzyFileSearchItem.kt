// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.filesFuzzy

import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.presentations.SeTargetItemPresentation
import com.intellij.platform.searchEverywhere.presentations.SeTargetItemPresentationBuilder
import com.intellij.util.text.matching.MatchedFragment
import org.jetbrains.annotations.ApiStatus

/**
 * SeItem implementation for fuzzy file search results.
 *
 * @property file The virtual file that matched the search
 * @property project The current project context
 * @property normalizedScore The normalized score (0.0 to 1.0) used for weight calculation
 * @property matchedIndices List of character indices in the filename that matched the pattern
 */
@ApiStatus.Internal
data class SeFuzzyFileSearchItem(
  override val rawObject: PSIPresentationBgRendererWrapper.PsiItemWithPresentation,
  val file: VirtualFile,
  private val project: Project,
  private val normalizedScore: Double,
  private val matchedIndices: List<Int>
) : SeItem {

  companion object {
    /**
     * File search weights range from ~0–2000 (non-start matches) up to ~10000–12000 (start matches,
     * via PreferStartMatchMatcherWrapper.START_MATCH_WEIGHT = 10000).
     * We cap fuzzy weights just below the start-match range so that:
     * - Best fuzzy results rank alongside good non-start file matches
     * - Start-matching file results always rank above fuzzy results
     */
    const val MAX_FUZZY_WEIGHT: Int = 9999
  }

  /**
   * Converts sorted character indices (e.g., [0, 1, 5, 6, 7]) into contiguous
   * [MatchedFragment] ranges (e.g., [MatchedFragment(0,2), MatchedFragment(5,8)]).
   */
  private fun indicesToFragments(indices: List<Int>): List<MatchedFragment> {
    if (indices.isEmpty()) return emptyList()
    val fragments = mutableListOf<MatchedFragment>()
    var start = indices[0]
    var end = start + 1
    for (i in 1 until indices.size) {
      val idx = indices[i]
      if (idx == end) {
        end++
      }
      else {
        fragments.add(MatchedFragment(start, end))
        start = idx
        end = idx + 1
      }
    }
    fragments.add(MatchedFragment(start, end))
    return fragments
  }

  override fun weight(): Int = (normalizedScore * MAX_FUZZY_WEIGHT).toInt()

  override suspend fun presentation(): SeTargetItemPresentation {
    val icon = rawObject.presentation.icon
    val fileName = file.name
    val locationText = file.parent?.path

    return SeTargetItemPresentationBuilder()
      .withIcon(icon)
      .withPresentableText(fileName)
      .withPresentableTextMatchedRanges(indicesToFragments(matchedIndices))
      .withLocationText(locationText)
      .withMultiSelectionSupported(true)
      .build()
  }
}
