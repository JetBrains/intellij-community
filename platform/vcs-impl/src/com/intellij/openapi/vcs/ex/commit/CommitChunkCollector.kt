// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex.commit

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object CommitChunkCollector : CounterUsagesCollector() {
  private val group = EventLogGroup("vcs.commit.chunk", 1)

  override fun getGroup(): EventLogGroup = group

  private val IS_AMEND = EventFields.Boolean("amend")

  private val CHUNK_LINES_COUNT = EventFields.RoundedInt("chunk_lines") // 0 - DELETED
  private val MESSAGE_LINES_COUNT = EventFields.RoundedInt("message_lines")
  private val MESSAGE_SUBJECT_LENGTH = EventFields.RoundedInt("message_subject_length")

  private val COMMIT = group.registerVarargEvent("commit", IS_AMEND, CHUNK_LINES_COUNT, MESSAGE_LINES_COUNT, MESSAGE_SUBJECT_LENGTH)

  fun logCommit(isAmend: Boolean, linesChanged: Int, messageLinesCount: Int, messageSubjectLength: Int) {
    COMMIT.log(
      IS_AMEND.with(isAmend),
      CHUNK_LINES_COUNT.with(linesChanged),
      MESSAGE_LINES_COUNT.with(messageLinesCount),
      MESSAGE_SUBJECT_LENGTH.with(messageSubjectLength)
    )
  }
}