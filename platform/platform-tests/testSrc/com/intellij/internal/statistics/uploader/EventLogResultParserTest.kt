// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.uploader

import com.intellij.internal.statistic.uploader.events.*
import com.intellij.internal.statistic.uploader.events.ExternalSystemEventSerializer.deserialize
import com.intellij.internal.statistic.uploader.events.ExternalSystemEventSerializer.serialize
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.UsefulTestCase
import java.io.File

class EventLogResultParserTest : UsefulTestCase() {
  private val OLD_VERSION_RECORDER = "FUS"
  private val OLD_VERSION = 0

  private val RECORDER = "ABC"
  private val VERSION = 1

  fun test_parse_start_event_old_version() {
    assertEquals(ExternalUploadStartedEvent(1583419425124, OLD_VERSION_RECORDER), deserialize("1583419425124 STARTED", OLD_VERSION))
    assertEquals(ExternalUploadStartedEvent(1583317425114, OLD_VERSION_RECORDER), deserialize("1583317425114 STARTED", OLD_VERSION))
  }

  fun test_parse_start_event() {
    assertEquals(ExternalUploadStartedEvent(1583419425124, RECORDER), deserialize("1583419425124 STARTED ABC", VERSION))
    assertEquals(ExternalUploadStartedEvent(1583317425114, RECORDER), deserialize("1583317425114 STARTED ABC", VERSION))
  }

  fun test_parse_send_event_old_version() {
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 2, 1, 10, emptyList(), emptyList(), OLD_VERSION_RECORDER),
      deserialize("1583419435214 SEND 2 1 10", OLD_VERSION)
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 6, 0, 15, emptyList(), emptyList(), OLD_VERSION_RECORDER),
      deserialize("1583419435214 SEND 6 0 15", OLD_VERSION)
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 2, -1, 5, emptyList(), emptyList(), OLD_VERSION_RECORDER),
      deserialize("1583419435214 SEND 2 a 5", OLD_VERSION)
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, -1, -1, -1, emptyList(), emptyList(), OLD_VERSION_RECORDER),
      deserialize("1583419435214 SEND a b b", OLD_VERSION)
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, -1, 34, 50, emptyList(), emptyList(), OLD_VERSION_RECORDER),
      deserialize("1583419435214 SEND 12a 34 50", OLD_VERSION)
    )
  }

  fun test_parse_send_event() {
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 2, 1, 10, emptyList(), emptyList(), RECORDER),
      deserialize("1583419435214 SEND ABC 2 1 10", VERSION)
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 6, 0, 15, emptyList(), emptyList(), RECORDER),
      deserialize("1583419435214 SEND ABC 6 0 15", VERSION)
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 2, -1, 5, emptyList(), emptyList(), RECORDER),
      deserialize("1583419435214 SEND ABC 2 a 5", VERSION)
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, -1, -1, -1, emptyList(), emptyList(), RECORDER),
      deserialize("1583419435214 SEND ABC a b b", VERSION)
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, -1, 34, 50, emptyList(), emptyList(), RECORDER),
      deserialize("1583419435214 SEND ABC 12a 34 50", VERSION)
    )
  }

  fun test_parse_send_event_with_files_hashes_old_version() {
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 2, 1, 10, listOf("path_to_file1", "path_to_file2"), emptyList(), OLD_VERSION_RECORDER),
      deserialize("1583419435214 SEND 2 1 10 [cGF0aF90b19maWxlMQ==,cGF0aF90b19maWxlMg==]", OLD_VERSION)
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 2, 1, 10, emptyList(), emptyList(), OLD_VERSION_RECORDER),
      deserialize("1583419435214 SEND 2 1 10 []", OLD_VERSION)
    )
  }

  fun test_parse_send_event_with_files_hashes() {
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 2, 1, 10, listOf("path_to_file1", "path_to_file2"), emptyList(), RECORDER),
      deserialize("1583419435214 SEND ABC 2 1 10 [cGF0aF90b19maWxlMQ==,cGF0aF90b19maWxlMg==]", VERSION)
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 2, 1, 10, emptyList(), emptyList(), RECORDER),
      deserialize("1583419435214 SEND ABC 2 1 10 []", VERSION)
    )
  }

  fun test_parse_send_event_with_errors_old_version() {
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 1, 1, 10, emptyList(), emptyList(), OLD_VERSION_RECORDER),
      deserialize("1583419435214 SEND 1 1 10 [] []", OLD_VERSION)
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 1, 1, 10, emptyList(), arrayListOf(1), OLD_VERSION_RECORDER),
      deserialize("1583419435214 SEND 1 1 10 [] [1]", OLD_VERSION)
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 1, 2, 10, emptyList(), arrayListOf(1, 5), OLD_VERSION_RECORDER),
      deserialize("1583419435214 SEND 1 2 10 [] [1,5]", OLD_VERSION)
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 1, 3, 10, emptyList(), arrayListOf(1, 5, 400), OLD_VERSION_RECORDER),
      deserialize("1583419435214 SEND 1 3 10 [] [1,5,400]", OLD_VERSION)
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 1, 3, 10, emptyList(), emptyList(), OLD_VERSION_RECORDER),
      deserialize("1583419435214 SEND 1 3 10 [] 400", OLD_VERSION)
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 1, 3, 10, emptyList(), emptyList(), OLD_VERSION_RECORDER),
      deserialize("1583419435214 SEND 1 3 10 123 400", OLD_VERSION)
    )
  }

  fun test_parse_send_event_with_errors() {
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 1, 1, 10, emptyList(), emptyList(), RECORDER),
      deserialize("1583419435214 SEND ABC 1 1 10 [] []", VERSION)
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 1, 1, 10, emptyList(), arrayListOf(1), RECORDER),
      deserialize("1583419435214 SEND ABC 1 1 10 [] [1]", VERSION)
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 1, 2, 10, emptyList(), arrayListOf(1, 5), RECORDER),
      deserialize("1583419435214 SEND ABC 1 2 10 [] [1,5]", VERSION)
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 1, 3, 10, emptyList(), arrayListOf(1, 5, 400), RECORDER),
      deserialize("1583419435214 SEND ABC 1 3 10 [] [1,5,400]", VERSION)
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 1, 3, 10, emptyList(), emptyList(), RECORDER),
      deserialize("1583419435214 SEND ABC 1 3 10 [] 400", VERSION)
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 1, 3, 10, emptyList(), emptyList(), RECORDER),
      deserialize("1583419435214 SEND ABC 1 3 10 123 400", VERSION)
    )
  }

  fun test_parse_send_event_with_paths_and_errors_old_version() {
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 1, 3, 10, arrayListOf("path_to_file1"), arrayListOf(1, 5, 400), OLD_VERSION_RECORDER),
      deserialize("1583419435214 SEND 1 3 10 [cGF0aF90b19maWxlMQ==] [1,5,400]", OLD_VERSION)
    )
  }

  fun test_parse_send_event_with_paths_and_errors() {
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 1, 3, 10, arrayListOf("path_to_file1"), arrayListOf(1, 5, 400), RECORDER),
      deserialize("1583419435214 SEND ABC 1 3 10 [cGF0aF90b19maWxlMQ==] [1,5,400]", VERSION)
    )
  }

  fun test_parse_finished_event_old_version() {
    assertEquals(
      ExternalUploadFinishedEvent(1583419435214, "no-arguments", OLD_VERSION_RECORDER),
      deserialize("1583419435214 FINISHED no-arguments", OLD_VERSION)
    )
    assertEquals(
      ExternalUploadFinishedEvent(1583419435214, "no-files", OLD_VERSION_RECORDER),
      deserialize("1583419435214 FINISHED no-files", OLD_VERSION)
    )
    assertEquals(
      ExternalUploadFinishedEvent(1583419435214, null, OLD_VERSION_RECORDER),
      deserialize("1583419435214 FINISHED", OLD_VERSION)
    )
    assertEquals(
      ExternalUploadFinishedEvent(1583419435214, null, OLD_VERSION_RECORDER),
      deserialize("1583419435214 FINISHED        ", OLD_VERSION)
    )
    assertEquals(
      ExternalUploadFinishedEvent(1583419435214, "multi", OLD_VERSION_RECORDER),
      deserialize("1583419435214 FINISHED multi word message", OLD_VERSION)
    )
  }

  fun test_parse_finished_event() {
    assertEquals(
      ExternalUploadFinishedEvent(1583419435214, "no-arguments", RECORDER),
      deserialize("1583419435214 FINISHED ABC no-arguments", VERSION)
    )
    assertEquals(
      ExternalUploadFinishedEvent(1583419435214, "no-files", RECORDER),
      deserialize("1583419435214 FINISHED ABC no-files", VERSION)
    )
    assertEquals(
      ExternalUploadFinishedEvent(1583419435214, null, RECORDER),
      deserialize("1583419435214 FINISHED ABC", VERSION)
    )
    assertEquals(
      ExternalUploadFinishedEvent(1583419435214, null, RECORDER),
      deserialize("1583419435214 FINISHED ABC        ", VERSION)
    )
    assertEquals(
      ExternalUploadFinishedEvent(1583419435214, "multi", RECORDER),
      deserialize("1583419435214 FINISHED ABC multi word message", VERSION)
    )
  }

  fun test_parse_error_event_old_version() {
    assertEquals(
      ExternalSystemErrorEvent(1583419435214, "com.jetbrains.SomeException", OLD_VERSION_RECORDER),
      deserialize("1583419435214 ERROR com.jetbrains.SomeException", OLD_VERSION)
    )
  }

  fun test_parse_error_event() {
    assertEquals(
      ExternalSystemErrorEvent(1583419435214, "com.jetbrains.SomeException", RECORDER),
      deserialize("1583419435214 ERROR ABC com.jetbrains.SomeException", VERSION)
    )
  }

  fun test_parse_finished_event_failed_old_version() {
    assertNull(deserialize("FINISHED", OLD_VERSION))
    assertNull(deserialize("FINISHED multi word message", OLD_VERSION))
    assertNull(deserialize("FINISHED 1583419435214 message", OLD_VERSION))
    assertNull(deserialize("FINISHED message", OLD_VERSION))
  }

  fun test_parse_finished_event_failed() {
    assertNull(deserialize("FINISHED ABC", VERSION))
    assertNull(deserialize("FINISHED ABC multi word message", VERSION))
    assertNull(deserialize("FINISHED ABC 1583419435214 message", VERSION))
    assertNull(deserialize("FINISHED ABC message", VERSION))
  }

  fun test_parse_send_event_failed_old_version() {
    assertNull(deserialize("1583419435214 SEND", OLD_VERSION))
    assertNull(deserialize("1583419435214 SEND 2", OLD_VERSION))
    assertNull(deserialize("1583419435214 SEND 2 3", OLD_VERSION))
    assertNull(deserialize("1583419435214 SEND 2,3,5", OLD_VERSION))
    assertNull(deserialize("1583419435214 SEND,2,3,5", OLD_VERSION))
    assertNull(deserialize("SEND 2 3 4 5", OLD_VERSION))
    assertNull(deserialize("SEND 2 3 4", OLD_VERSION))
  }

  fun test_parse_send_event_failed() {
    assertNull(deserialize("1583419435214 SEND ABC", VERSION))
    assertNull(deserialize("1583419435214 SEND ABC 2", VERSION))
    assertNull(deserialize("1583419435214 SEND ABC 2 3", VERSION))
    assertNull(deserialize("1583419435214 SEND ABC 2,3,5", VERSION))
    assertNull(deserialize("1583419435214 SEND ABC,2,3,5", VERSION))
    assertNull(deserialize("SEND ABC 2 3 4 5", VERSION))
    assertNull(deserialize("SEND ABC 2 3 4", VERSION))
  }

  fun test_parse_send_event_if_files_hashes_incorrect_old_version() {
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 2, 1, 10, emptyList(), emptyList(), OLD_VERSION_RECORDER),
      deserialize("1583419435214 SEND 2 1 10 qwerty", OLD_VERSION)
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 2, 1, 10, emptyList(), emptyList(), OLD_VERSION_RECORDER),
      deserialize("1583419435214 SEND 2 1 10 [']", OLD_VERSION)
    )
  }

  fun test_parse_send_event_if_files_hashes_incorrect() {
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 2, 1, 10, emptyList(), emptyList(), RECORDER),
      deserialize("1583419435214 SEND ABC 2 1 10 qwerty", VERSION)
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 2, 1, 10, emptyList(), emptyList(), RECORDER),
      deserialize("1583419435214 SEND ABC 2 1 10 [']", VERSION)
    )
  }

  fun test_parse_error_event_failed_old_version() {
    assertNull(deserialize("1583419435214 ERROR", OLD_VERSION))
    assertNull(deserialize("ERROR com.jetbrains.SomeException", OLD_VERSION))
    assertNull(deserialize("1583419435214 ERROR com.jetbrains.SomeException more.messages", OLD_VERSION))
  }

  fun test_parse_error_event_failed() {
    assertNull(deserialize("1583419435214 ERROR ABC", VERSION))
    assertNull(deserialize("ERROR ABC com.jetbrains.SomeException", VERSION))
    assertNull(deserialize("1583419435214 ERROR ABC com.jetbrains.SomeException more.messages", VERSION))
  }

  fun test_parse_unknown_event_failed_old_version() {
    assertNull(deserialize("SEN", OLD_VERSION))
    assertNull(deserialize("STARTEDD", OLD_VERSION))
    assertNull(deserialize("FINISH", OLD_VERSION))
    assertNull(deserialize("FAIL", OLD_VERSION))
    assertNull(deserialize("UNKNOWN", OLD_VERSION))
    assertNull(deserialize("UNKNOWN with-message", OLD_VERSION))
    assertNull(deserialize("UNKNOWN 1 23 4", OLD_VERSION))

    assertNull(deserialize("STARTED", OLD_VERSION))
    assertNull(deserialize("1583419435214 SEN", OLD_VERSION))
    assertNull(deserialize("1583419435214 STARTEDD", OLD_VERSION))
    assertNull(deserialize("1583419435214 FINISH", OLD_VERSION))
    assertNull(deserialize("1583419435214 FAIL", OLD_VERSION))
    assertNull(deserialize("1583419435214 UNKNOWN", OLD_VERSION))
    assertNull(deserialize("1583419435214 UNKNOWN with-message", OLD_VERSION))
    assertNull(deserialize("1583419435214 UNKNOWN 1 23 4", OLD_VERSION))
  }

  fun test_parse_unknown_event_failed() {
    assertNull(deserialize("SEN", VERSION))
    assertNull(deserialize("STARTEDD", VERSION))
    assertNull(deserialize("FINISH", VERSION))
    assertNull(deserialize("FAIL", VERSION))
    assertNull(deserialize("UNKNOWN", VERSION))
    assertNull(deserialize("UNKNOWN with-message", VERSION))
    assertNull(deserialize("UNKNOWN 1 23 4", VERSION))

    assertNull(deserialize("SEN ABC", VERSION))
    assertNull(deserialize("STARTEDD ABC", VERSION))
    assertNull(deserialize("FINISH ABC", VERSION))
    assertNull(deserialize("FAIL ABC", VERSION))
    assertNull(deserialize("UNKNOWN ABC", VERSION))
    assertNull(deserialize("UNKNOWN ABC with-message", VERSION))
    assertNull(deserialize("UNKNOWN ABC 1 23 4", VERSION))

    assertNull(deserialize("STARTED", VERSION))
    assertNull(deserialize("1583419435214 SEN", VERSION))
    assertNull(deserialize("1583419435214 STARTEDD", VERSION))
    assertNull(deserialize("1583419435214 FINISH", VERSION))
    assertNull(deserialize("1583419435214 FAIL", VERSION))
    assertNull(deserialize("1583419435214 UNKNOWN", VERSION))
    assertNull(deserialize("1583419435214 UNKNOWN with-message", VERSION))
    assertNull(deserialize("1583419435214 UNKNOWN 1 23 4", VERSION))

    assertNull(deserialize("STARTED ABC", VERSION))
    assertNull(deserialize("1583419435214 SEN ABC", VERSION))
    assertNull(deserialize("1583419435214 STARTEDD ABC", VERSION))
    assertNull(deserialize("1583419435214 FINISH ABC", VERSION))
    assertNull(deserialize("1583419435214 FAIL ABC", VERSION))
    assertNull(deserialize("1583419435214 UNKNOWN ABC", VERSION))
    assertNull(deserialize("1583419435214 UNKNOWN ABC with-message", VERSION))
    assertNull(deserialize("1583419435214 UNKNOWN ABC 1 23 4", VERSION))
  }

  fun test_serialize_start_event() {
    assertEquals("1583419435214 STARTED ABC", serialize(ExternalUploadStartedEvent(1583419435214, RECORDER)))
  }

  fun test_serialize_send_event() {
    assertEquals(
      "1583419435214 SEND ABC 1 2 3 [cGF0aF90b19maWxl] []",
      serialize(ExternalUploadSendEvent(1583419435214, 1, 2, 3, listOf("path_to_file"), emptyList(), RECORDER))
    )
    assertEquals(
      "1583419435214 SEND ABC 2 -1 12 [cGF0aF90b19maWxlMQ==,cGF0aF90b19maWxlMg==] []",
      serialize(ExternalUploadSendEvent(1583419435214, 2, -1, 12, listOf("path_to_file1", "path_to_file2"), emptyList(), RECORDER))
    )
  }

  fun test_serialize_send_event_without_path() {
    assertEquals(
      "1583419435214 SEND ABC 0 2 3 [] []",
      serialize(ExternalUploadSendEvent(1583419435214, 0, 2, 3, emptyList(), emptyList(), RECORDER))
    )
  }

  fun test_serialize_send_event_without_path_but_with_errors() {
    assertEquals(
      "1583419435214 SEND ABC 0 3 3 [] [400,1,5]",
      serialize(ExternalUploadSendEvent(1583419435214, 0, 3, 3, emptyList(), arrayListOf(400, 1, 5), RECORDER))
    )
    assertEquals(
      "1583419435214 SEND ABC 0 2 3 [] [400,50]",
      serialize(ExternalUploadSendEvent(1583419435214, 0, 2, 3, emptyList(), arrayListOf(400, 50), RECORDER))
    )
    assertEquals(
      "1583419435214 SEND ABC 0 1 3 [] [-10]",
      serialize(ExternalUploadSendEvent(1583419435214, 0, 1, 3, emptyList(), arrayListOf(-10), RECORDER))
    )
  }

  fun test_serialize_send_event_with_errors() {
    assertEquals(
      "1583419435214 SEND ABC 1 2 3 [cGF0aF90b19maWxl] [50,400]",
      serialize(ExternalUploadSendEvent(1583419435214, 1, 2, 3, listOf("path_to_file"), arrayListOf(50, 400), RECORDER))
    )
    assertEquals(
      "1583419435214 SEND ABC 2 -1 12 [cGF0aF90b19maWxlMQ==,cGF0aF90b19maWxlMg==] [400]",
      serialize(ExternalUploadSendEvent(1583419435214, 2, -1, 12, listOf("path_to_file1", "path_to_file2"), arrayListOf(400), RECORDER))
    )
  }

  fun test_serialize_failed_event() {
    assertEquals(
      "1583419435214 FINISHED ABC error-on-send",
      serialize(ExternalUploadFinishedEvent(1583419435214, "error-on-send", RECORDER))
    )

    assertEquals(
      "1583419435214 FINISHED ABC message",
      serialize(ExternalUploadFinishedEvent(1583419435214, "message", RECORDER))
    )

    assertEquals(
      "1583419435214 FINISHED ABC multi_word_message",
      serialize(ExternalUploadFinishedEvent(1583419435214, "multi word message", RECORDER))
    )

    assertEquals(
      "1583419435214 FINISHED ABC",
      serialize(ExternalUploadFinishedEvent(1583419435214, "", RECORDER))
    )

    assertEquals(
      "1583419435214 FINISHED ABC",
      serialize(ExternalUploadFinishedEvent(1583419435214, null, RECORDER))
    )
  }

  fun test_serialize_error_event() {
    assertEquals(
      "1583419435214 ERROR ABC com.jetbrains.SomeException",
      serialize(ExternalSystemErrorEvent(1583419435214,"com.jetbrains.SomeException", RECORDER))
    )

    assertEquals(
      "1583419435214 ERROR ABC SomeException",
      serialize(ExternalSystemErrorEvent(1583419435214, "SomeException", RECORDER))
    )

    val ex = MockEventLogCustomException()
    assertEquals(
      "1583419435214 ERROR ABC com.intellij.internal.statistics.uploader.MockEventLogCustomException",
      serialize(ExternalSystemErrorEvent(1583419435214, ex, RECORDER))
    )
  }

  private fun doTestParseOldFile(fileText: String, vararg expectedEvents: ExternalSystemEvent) {
    doTestParseFileInternal(fileText, 0, *expectedEvents)
  }

  private fun doTestParseFile(fileText: String, vararg expectedEvents: ExternalSystemEvent) {
    doTestParseFileInternal(fileText, 1, *expectedEvents)
  }

  private fun doTestParseFileInternal(fileText: String, version: Int, vararg expectedEvents: ExternalSystemEvent) {
    val directory = FileUtil.createTempDirectory("idea-external-send-result", null)
    try {
      val filename = if (version == 0) "idea_statistics_uploader_events.log" else "idea_statistics_uploader_events_v${version}.log"
      FileUtil.writeToFile(File(directory, filename), fileText)
      val actual = ExternalEventsLogger.parseEvents(directory)
      assertEquals(expectedEvents.size, actual.size)
      for ((index, expectedEvent) in expectedEvents.withIndex()) {
        assertEquals(expectedEvent, actual[index])
      }
    }
    finally {
      directory.deleteRecursively()
    }
  }

  fun test_parse_external_send_started_result_file_old_version() {
    doTestParseOldFile(
      "1583419435214 STARTED",
      ExternalUploadStartedEvent(1583419435214, OLD_VERSION_RECORDER)
    )
  }

  fun test_parse_external_send_started_result_file() {
    doTestParseFile(
      "1583419435214 STARTED ABC",
      ExternalUploadStartedEvent(1583419435214, RECORDER)
    )
  }

  fun test_parse_external_send_succeed_result_file_old_version() {
    doTestParseOldFile(
      "1583419435214 STARTED\n1583419435234 SEND 1 2 3",
      ExternalUploadStartedEvent(1583419435214, OLD_VERSION_RECORDER),
      ExternalUploadSendEvent(1583419435234, 1, 2, 3, emptyList(), emptyList(), OLD_VERSION_RECORDER)
    )
  }

  fun test_parse_external_send_succeed_result_file() {
    doTestParseFile(
      "1583419435214 STARTED ABC\n1583419435234 SEND ABC 1 2 3",
      ExternalUploadStartedEvent(1583419435214, RECORDER),
      ExternalUploadSendEvent(1583419435234, 1, 2, 3, emptyList(), emptyList(), RECORDER)
    )
  }

  fun test_parse_external_send_failed_result_file_old_version() {
    doTestParseOldFile(
      "1583419435214 STARTED\n1583419435218 FINISHED error-on-send",
      ExternalUploadStartedEvent(1583419435214, OLD_VERSION_RECORDER),
      ExternalUploadFinishedEvent(1583419435218, "error-on-send", OLD_VERSION_RECORDER)
    )
  }

  fun test_parse_external_send_failed_result_file() {
    doTestParseFile(
      "1583419435214 STARTED ABC\n1583419435218 FINISHED ABC error-on-send",
      ExternalUploadStartedEvent(1583419435214, RECORDER),
      ExternalUploadFinishedEvent(1583419435218, "error-on-send", RECORDER)
    )
  }

  fun test_parse_external_send_with_ending_lineseparator_old_version() {
    doTestParseOldFile(
      "1583419435218 STARTED\n",
      ExternalUploadStartedEvent(1583419435218, OLD_VERSION_RECORDER)
    )
  }

  fun test_parse_external_send_with_ending_lineseparator() {
    doTestParseFile(
      "1583419435218 STARTED ACB\n",
      ExternalUploadStartedEvent(1583419435218, RECORDER)
    )
  }

  fun test_parse_external_send_with_multiple_lineseparators_old_version() {
    doTestParseOldFile(
      "1583419435218 STARTED\n\n\n",
      ExternalUploadStartedEvent(1583419435218, OLD_VERSION_RECORDER)
    )
  }

  fun test_parse_external_send_with_multiple_lineseparators() {
    doTestParseFile(
      "1583419435218 STARTED ABC\n\n\n",
      ExternalUploadStartedEvent(1583419435218, RECORDER)
    )
  }

  fun test_parse_external_send_with_multiple_events_including_invalid_old_version() {
    doTestParseOldFile(
      "1583419435218 STARTED\n1583419435219 UNKNOWN\n1583419435220 FINISHED error",
      ExternalUploadStartedEvent(1583419435218, OLD_VERSION_RECORDER),
      ExternalUploadFinishedEvent(1583419435220, "error", OLD_VERSION_RECORDER)
    )
  }

  fun test_parse_external_send_with_multiple_events_including_invalid() {
    doTestParseFile(
      "1583419435218 STARTED ABC\n1583419435219 UNKNOWN ABC\n1583419435220 FINISHED ABC error",
      ExternalUploadStartedEvent(1583419435218, RECORDER),
      ExternalUploadFinishedEvent(1583419435220, "error", RECORDER)
    )
  }

  fun test_parse_external_send_with_invalid_event_old_version() {
    doTestParseOldFile("UNKNOWN")
  }

  fun test_parse_external_send_with_invalid_event() {
    doTestParseFile("UNKNOWN")
  }

  fun test_parse_external_send_started_without_timestamp_old_version() {
    doTestParseOldFile(
      "STARTED"
    )
  }

  fun test_parse_external_send_started_without_timestamp() {
    doTestParseFile(
      "STARTED"
    )
  }

  fun test_parse_external_send_multiple_events_without_timestamp_old_version() {
    doTestParseOldFile(
      "STARTED\nSEND 1 2 3"
    )
  }

  fun test_parse_external_send_multiple_events_without_timestamp() {
    doTestParseFile(
      "STARTED\nSEND 1 2 3"
    )
  }

  fun test_parse_external_send_with_empty_file_old_version() {
    doTestParseOldFile("")
  }

  fun test_parse_external_send_with_empty_file() {
    doTestParseFile("")
  }

  fun test_parse_external_send_with_multiple_recorders() {
    doTestParseFile(
      "1583419435214 STARTED ALL\n1583419435218 FINISHED ABC error-on-send\n1583419436818 FINISHED DEF",
      ExternalUploadStartedEvent(1583419435214, "ALL"),
      ExternalUploadFinishedEvent(1583419435218, "error-on-send", "ABC"),
      ExternalUploadFinishedEvent(1583419436818, null, "DEF")
    )
  }
}

private class MockEventLogCustomException : Exception()