// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.ai.review

import com.intellij.agent.workbench.ai.review.model.AIReviewAgent
import com.intellij.agent.workbench.ai.review.model.AIReviewViewModel
import com.intellij.agent.workbench.ai.review.model.AIReviewViewModel.State
import com.intellij.agent.workbench.ai.review.model.ReviewRating
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change

internal object AIReviewCollector : CounterUsagesCollector() {
  private val group = EventLogGroup("agent.workbench.ai.review", 1)

  private val REQUEST_ID = EventFields.Long("request_id", "ID of the review request.")
  private val RATING = EventFields.Enum("rating", ReviewRating::class.java)
  private val feedbackEvent = group.registerVarargEvent("feedback", RATING, REQUEST_ID)

  private val FILES_COUNT = EventFields.Int("files_count")
  private val LANGUAGES_COUNT = EventFields.Int("languages_count")
  private val LANGUAGE1 = EventFields.Language("language1")
  private val LANGUAGE_PERCENTAGE1 = EventFields.Int("lang_percentage1")
  private val LANGUAGE2 = EventFields.Language("language2")
  private val LANGUAGE_PERCENTAGE2 = EventFields.Int("lang_percentage2")
  private val LANGUAGE3 = EventFields.Language("language3")
  private val LANGUAGE_PERCENTAGE3 = EventFields.Int("lang_percentage3")

  private val CHANGES_COUNT = EventFields.Int("changes_count")
  private val NEW_FILES = EventFields.Int("new_files")
  private val DELETED_FILES = EventFields.Int("deleted_files")
  private val MODIFIED_FILES = EventFields.Int("modified_files")
  private val MOVED_FILES = EventFields.Int("moved_files")
  private val RENAMED_FILES = EventFields.Int("renamed_files")

  private val LINES_ADDED = EventFields.Int("lines_added")
  private val LINES_REMOVED = EventFields.Int("lines_removed")
  private val CHARS_ADDED = EventFields.Int("chars_added")
  private val CHARS_REMOVED = EventFields.Int("chars_removed")

  private val SUCCESS = EventFields.Boolean("success")

  private val AGENT = EventFields.Enum("agent", AIReviewAgentFus::class.java)

  private data class ChangeMetrics(
    val changesCount: Int,
    val newFiles: Int,
    val deletedFiles: Int,
    val modifiedFiles: Int,
    val movedFiles: Int,
    val renamedFiles: Int,
    val linesAdded: Int,
    val linesRemoved: Int,
    val charsAdded: Int,
    val charsRemoved: Int,
  )

  internal val reviewActivity = group.registerIdeActivity("review",
                                                          startEventAdditionalFields =
                                                            arrayOf(REQUEST_ID, CHANGES_COUNT, NEW_FILES, DELETED_FILES, MODIFIED_FILES,
                                                                    MOVED_FILES, RENAMED_FILES, LINES_ADDED, LINES_REMOVED,
                                                                    CHARS_ADDED, CHARS_REMOVED,
                                                                    LANGUAGE1, LANGUAGE_PERCENTAGE1,
                                                                    LANGUAGE2, LANGUAGE_PERCENTAGE2,
                                                                    LANGUAGE3, LANGUAGE_PERCENTAGE3,
                                                                    FILES_COUNT, LANGUAGES_COUNT, AGENT),
                                                          finishEventAdditionalFields = arrayOf(REQUEST_ID, SUCCESS))

  override fun getGroup(): EventLogGroup = group

  fun logFeedback(project: Project?,
                  requestId: Long,
                  rating: ReviewRating) {

    if (rating == ReviewRating.None) return

    feedbackEvent.log(project) {
      add(RATING.with(rating))
      add(REQUEST_ID.with(requestId))
    }
  }

  fun logReviewStarted(project: Project?, model: AIReviewViewModel): StructuredIdeActivity {
    val languagesWithCount =
      model.changes.value
        .asSequence()
        .mapNotNull { it.virtualFile }
        .mapNotNull { LanguageUtil.getFileLanguage(it) }
        .groupingBy { it }
        .eachCount()

    val totalCount = languagesWithCount.values.sum()
    val languagesCount = languagesWithCount.entries.count()

    val sortedLanguages = languagesWithCount.entries.sortedByDescending { it.value }

    val mostUsedLanguage1 = sortedLanguages.getOrNull(0)?.key
    val mostUsedLanguage2 = sortedLanguages.getOrNull(1)?.key
    val mostUsedLanguage3 = sortedLanguages.getOrNull(2)?.key

    var languagePercentage1: Int? = null
    var languagePercentage2: Int? = null
    var languagePercentage3: Int? = null
    var totalFilesCount: Int? = null

    if (totalCount > 0) {
      sortedLanguages.getOrNull(0)?.let { entry ->
        languagePercentage1 = (entry.value * 100) / totalCount
      }

      sortedLanguages.getOrNull(1)?.let { entry ->
        languagePercentage2 = (entry.value * 100) / totalCount
      }

      sortedLanguages.getOrNull(2)?.let { entry ->
        languagePercentage3 = (entry.value * 100) / totalCount
      }

      totalFilesCount = totalCount
    }

    val eventPairs = listOfNotNull(
      REQUEST_ID with model.getCurrentRequest().requestId,
      LANGUAGES_COUNT with languagesCount,
      mostUsedLanguage1?.let { language -> LANGUAGE1 with language },
      languagePercentage1?.let { percentage -> LANGUAGE_PERCENTAGE1 with percentage },
      mostUsedLanguage2?.let { language -> LANGUAGE2 with language },
      languagePercentage2?.let { percentage -> LANGUAGE_PERCENTAGE2 with percentage },
      mostUsedLanguage3?.let { language -> LANGUAGE3 with language },
      languagePercentage3?.let { percentage -> LANGUAGE_PERCENTAGE3 with percentage },
      totalFilesCount?.let { filesCount -> FILES_COUNT with filesCount },
      model.getCurrentRequest().selectedAgent?.let { AGENT with it.toFusAgent() }
    ).toMutableList()

    calculateChangeMetrics(model.changes.value).apply {
      eventPairs += listOf(
        CHANGES_COUNT with changesCount,
        NEW_FILES with newFiles,
        DELETED_FILES with deletedFiles,
        MODIFIED_FILES with modifiedFiles,
        MOVED_FILES with movedFiles,
        RENAMED_FILES with renamedFiles,
        LINES_ADDED with linesAdded,
        LINES_REMOVED with linesRemoved,
        CHARS_ADDED with charsAdded,
        CHARS_REMOVED with charsRemoved,
      )
    }

    return reviewActivity.started(project) { eventPairs }
  }

  fun logReviewComplete(reviewActivity: StructuredIdeActivity, state: State) {
    val requestId = (state as? State.RequestHolder)?.request?.requestId ?: let {
      fileLogger().warn("No request ID found for review activity ${reviewActivity.id}")
      -1L
    }
    val success = state !is State.Error && state !is State.Cancelled

    if (reviewActivity.isFinished()) return
    synchronized(this) {
      if (reviewActivity.isFinished()) return

      reviewActivity.finished {
        listOf(
          REQUEST_ID with requestId,
          SUCCESS with success
        )
      }
    }
  }

  private fun calculateChangeMetrics(changes: List<Change>): ChangeMetrics {
    var newFiles = 0
    var deletedFiles = 0
    var modifiedFiles = 0
    var movedFiles = 0
    var renamedFiles = 0

    var linesAdded = 0
    var linesRemoved = 0
    var charsAdded = 0
    var charsRemoved = 0

    for (change in changes) {
      when (change.type) {
        Change.Type.NEW -> newFiles++
        Change.Type.DELETED -> deletedFiles++
        Change.Type.MODIFICATION -> modifiedFiles++
        Change.Type.MOVED -> movedFiles++
      }

      if (change.isRenamed()) {
        renamedFiles++
      }

      try {
        val beforeContent = change.beforeRevision?.content
        val afterContent = change.afterRevision?.content

        if (afterContent != null) {
          linesAdded += afterContent.lines().size
          charsAdded += afterContent.length
        }
        else if (beforeContent != null) {
          linesRemoved += beforeContent.lines().size
          charsRemoved += beforeContent.length
        }
      }
      catch (e: VcsException) {
        logger<AIReviewCollector>().warn("Exception during processing $change content", e)
      }
    }

    return ChangeMetrics(
      changesCount = changes.size,
      newFiles = newFiles,
      deletedFiles = deletedFiles,
      modifiedFiles = modifiedFiles,
      movedFiles = movedFiles,
      renamedFiles = renamedFiles,
      linesAdded = linesAdded,
      linesRemoved = linesRemoved,
      charsAdded = charsAdded,
      charsRemoved = charsRemoved,
    )
  }

  @Suppress("HardCodedStringLiteral")
  private fun AIReviewAgent.toFusAgent(): AIReviewAgentFus {
    return when {
      displayName.contains("Codex", ignoreCase = true) -> AIReviewAgentFus.Codex
      displayName.contains("Claude Code", ignoreCase = true) -> AIReviewAgentFus.ClaudeCode
      else -> AIReviewAgentFus.Unknown
    }
  }

  internal enum class AIReviewAgentFus {
    Codex,
    ClaudeCode,
    Unknown,
  }
}