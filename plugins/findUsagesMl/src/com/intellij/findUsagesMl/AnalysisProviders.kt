package com.intellij.findUsagesMl

import com.jetbrains.mlapi.feature.Feature
import com.jetbrains.mlapi.feature.FeatureContainer
import com.jetbrains.mlapi.feature.FeatureDeclaration

data class FindUsagesFileRankingAnalysisInfo(
  val customSessionId: Long,
  val isUsage: Boolean,
  val timestamp: Long,
  val isSearchValid: Boolean = true,
  val numberOfUsageFiles: Int = -1,
  val numberOfCandidates: Int = -1,
  val indexInOriginalOrder: Int = -1,
  val activeSessionId: Long = -1,
  val finishSessionId: Long = -1,
) {
  constructor(customSessionId: Long, isUsage: Boolean, timestamp: Long, isSearchValid: Boolean, numberOfUsageFiles: Int?, numberOfCandidates: Int?, indexInOriginalOrder: Int?, activeSessionId: Long?, finishSessionId: Long?)
    : this(customSessionId,
           isUsage,
           timestamp,
           isSearchValid,
           numberOfUsageFiles ?: -1,
           numberOfCandidates ?: -1,
           indexInOriginalOrder ?: -1,
           activeSessionId ?: -1,
           finishSessionId ?: -1)
}

object FindUsagesFileRankerAnalysisTargets : FeatureContainer {
  val SESSION_ID: FeatureDeclaration<Long> = FeatureDeclaration.long("session_id") { "Id of the search session" }

  val IS_USAGE: FeatureDeclaration<Boolean> = FeatureDeclaration.boolean("is_usage") { "Is usage" }
  val SEARCH_TIMESTAMP: FeatureDeclaration<Long> = FeatureDeclaration.long("search_timestamp_ms") { "Search timestamp" }
  val IS_VALID: FeatureDeclaration<Boolean> = FeatureDeclaration.boolean("is_valid") { "Is the search session valid (false if corrupted)" }

  val NUMBER_OF_USAGE_FILES: FeatureDeclaration<Int> = FeatureDeclaration.int("number_of_usage_files") { "Number of files containing a usage" }
  val NUMBER_OF_CANDIDATES: FeatureDeclaration<Int> = FeatureDeclaration.int("number_of_candidates") { "Number of candidates" }
  val INDEX_IN_ORIGINAL_ORDER: FeatureDeclaration<Int> = FeatureDeclaration.int("index_in_original_order") { "Index in original order" }

  val ACTIVE_SESSION: FeatureDeclaration<Long> = FeatureDeclaration.long("active_session") { "Id of last active (started) session" }
  val FINISH_SESSION: FeatureDeclaration<Long> = FeatureDeclaration.long("finish_session") { "Id of finishing session" }
}

class FindUsagesFileRankerAnalysisProvider {
  fun provideAnalysisTargets(info: FindUsagesFileRankingAnalysisInfo): List<Feature> {
    if (!info.isSearchValid) {
      return listOf(
        FindUsagesFileRankerAnalysisTargets.SESSION_ID with info.customSessionId,
        FindUsagesFileRankerAnalysisTargets.SEARCH_TIMESTAMP with info.timestamp,
        FindUsagesFileRankerAnalysisTargets.IS_VALID with false,
        FindUsagesFileRankerAnalysisTargets.ACTIVE_SESSION with info.activeSessionId,
        FindUsagesFileRankerAnalysisTargets.FINISH_SESSION with info.finishSessionId
      )
    }
    return listOf(
      FindUsagesFileRankerAnalysisTargets.SESSION_ID with info.customSessionId,
      FindUsagesFileRankerAnalysisTargets.IS_VALID with true,
      FindUsagesFileRankerAnalysisTargets.IS_USAGE with info.isUsage,
      FindUsagesFileRankerAnalysisTargets.SEARCH_TIMESTAMP with info.timestamp,
      FindUsagesFileRankerAnalysisTargets.NUMBER_OF_USAGE_FILES with info.numberOfUsageFiles,
      FindUsagesFileRankerAnalysisTargets.NUMBER_OF_CANDIDATES with info.numberOfCandidates,
      FindUsagesFileRankerAnalysisTargets.INDEX_IN_ORIGINAL_ORDER with info.indexInOriginalOrder
    )
  }
}