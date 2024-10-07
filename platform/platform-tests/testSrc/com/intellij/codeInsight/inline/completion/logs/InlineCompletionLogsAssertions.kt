// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.logs.InlineCompletionUsageTracker.ShownEvents.FinishType
import com.intellij.codeInsight.inline.completion.logs.TestInlineCompletionLogs.SingleSessionLog
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun SingleSessionLog.assertSomeContextLogsPresent() {
  assertPresent("file_language")
  assertPresent("line_number")
  assertPresent("column_number")
}

@ApiStatus.Internal
fun SingleSessionLog.assertFinishType(expected: FinishType) {
  assert("finish_type", expected.name)
}

@ApiStatus.Internal
fun SingleSessionLog.assertInvalidationEvent(invalidationEvent: Class<out InlineCompletionEvent>) {
  assert("invalidation_event", invalidationEvent.name)
}

@ApiStatus.Internal
fun SingleSessionLog.assertWasShown(expected: Boolean) {
  assert("was_shown", expected)
  if (expected) {
    assertPresent("showing_time")
    assertPresent("time_to_start_showing")
  }
  else {
    assertAbsence("showing_time")
    assertAbsence("time_to_start_showing")
  }
}

@ApiStatus.Internal
fun SingleSessionLog.assertRequestIdPresent() {
  assertPresent("request_id")
}

@ApiStatus.Internal
fun SingleSessionLog.assertProposalLengthAndLines(
  received_proposal_length: Int,
  received_proposal_lines: Int,
  finish_proposal_length: Int,
  finish_proposal_lines: Int,
) {
  assert("received_proposal_length", received_proposal_length)
  assert("received_proposal_lines", received_proposal_lines)
  assert("finish_proposal_length", finish_proposal_length)
  assert("finish_proposal_lines", finish_proposal_lines)
}

@ApiStatus.Internal
fun SingleSessionLog.assertTotalInsertionLogs(total_inserted_length: Int, total_inserted_lines: Int) {
  assert("total_inserted_length", total_inserted_length)
  assert("total_inserted_lines", total_inserted_lines)
}