package com.intellij.findUsagesMl

import com.jetbrains.ml.api.logs.BooleanEventField
import com.jetbrains.ml.api.logs.EventField
import com.jetbrains.ml.api.logs.EventPair
import com.jetbrains.ml.api.logs.IntEventField
import com.jetbrains.ml.api.logs.LongEventField
import com.jetbrains.ml.tools.logs.extractEventFields

data class FindUsagesFileRankingAnalysisInfo(
  val isUsage: Boolean,
  val timestamp: Long,
  val isSearchValid: Boolean = true,
  val numberOfUsageFiles: Int = -1,
  val numberOfCandidates: Int = -1,
  val activeSessionId: Long = -1,
  val finishSessionId: Long = -1,
) {
  constructor(isUsage: Boolean, timestamp: Long, isSearchValid: Boolean, numberOfUsageFiles: Int?, numberOfCandidates: Int?, activeSessionId: Long?, finishSessionId: Long?)
    : this(isUsage,
           timestamp,
           isSearchValid,
           numberOfUsageFiles ?: -1,
           numberOfCandidates ?: -1,
           activeSessionId ?: -1,
           finishSessionId ?: -1)
}

object FindUsagesFileRankerAnalysisTargets {
  val IS_USAGE: BooleanEventField = BooleanEventField("is_usage", lazyDescription = { "Is usage" })
  val SEARCH_TIMESTAMP: LongEventField = LongEventField("search_timestamp_ms", lazyDescription = { "Search timestamp" })
  val IS_VALID: BooleanEventField = BooleanEventField(name = "is_valid", lazyDescription = { "Is the search session valid (false if corrupted)" })

  val NUMBER_OF_USAGE_FILES: IntEventField = IntEventField(name = "number_of_usage_files", lazyDescription = { "Number of files containing a usage" })
  val NUMBER_OF_CANDIDATES: IntEventField = IntEventField(name = "number_of_candidates", lazyDescription = { "Number of candidates" })

  val ACTIVE_SESSION: LongEventField = LongEventField(name = "active_session", lazyDescription = { "Id of last active (started) session" })
  val FINISH_SESSION: LongEventField = LongEventField(name = "finish_session", lazyDescription = { "Id of finishing session" })

  fun eventFields(): List<List<EventField<*>>> {
    return listOf(extractEventFields(FindUsagesFileRankerAnalysisTargets))
  }
}

class FindUsagesFileRankerAnalysisProvider {
  fun provideAnalysisTargets(info: FindUsagesFileRankingAnalysisInfo): List<EventPair<*>> {
    if (!info.isSearchValid) {
      return listOf(
        FindUsagesFileRankerAnalysisTargets.SEARCH_TIMESTAMP with info.timestamp,
        FindUsagesFileRankerAnalysisTargets.IS_VALID with false,
        FindUsagesFileRankerAnalysisTargets.ACTIVE_SESSION with info.activeSessionId,
        FindUsagesFileRankerAnalysisTargets.FINISH_SESSION with info.finishSessionId
      )
    }
    return listOf(
      FindUsagesFileRankerAnalysisTargets.IS_VALID with true,
      FindUsagesFileRankerAnalysisTargets.IS_USAGE with info.isUsage,
      FindUsagesFileRankerAnalysisTargets.SEARCH_TIMESTAMP with info.timestamp,
      FindUsagesFileRankerAnalysisTargets.NUMBER_OF_USAGE_FILES with info.numberOfUsageFiles,
      FindUsagesFileRankerAnalysisTargets.NUMBER_OF_CANDIDATES with info.numberOfCandidates
    )
  }
}