// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.uploader

import com.intellij.internal.statistic.uploader.events.*
import com.intellij.internal.statistic.uploader.events.ExternalSystemEventSerializer.deserialize
import com.intellij.internal.statistic.uploader.events.ExternalSystemEventSerializer.serialize
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.UsefulTestCase
import java.io.File

class EventLogResultParserTest : UsefulTestCase() {

  fun test_parse_start_event() {
    assertEquals(deserialize("1583419425124 STARTED"), ExternalUploadStartedEvent(1583419425124))
    assertEquals(deserialize("1583317425114 STARTED"), ExternalUploadStartedEvent(1583317425114))
  }

  fun test_parse_send_event() {
    assertEquals(deserialize("1583419435214 SEND 2 1 10"), ExternalUploadSendEvent(1583419435214, 2, 1, 10))
    assertEquals(deserialize("1583419435214 SEND 6 0 15"), ExternalUploadSendEvent(1583419435214, 6, 0, 15))
    assertEquals(deserialize("1583419435214 SEND 2 a 5"), ExternalUploadSendEvent(1583419435214, 2, -1, 5))
    assertEquals(deserialize("1583419435214 SEND a b b"), ExternalUploadSendEvent(1583419435214, -1, -1, -1))
    assertEquals(deserialize("1583419435214 SEND 12a 34 50"), ExternalUploadSendEvent(1583419435214, -1, 34, 50))
  }

  fun test_parse_finished_event() {
    assertEquals(deserialize("1583419435214 FINISHED no-arguments"), ExternalUploadFinishedEvent(1583419435214, "no-arguments"))
    assertEquals(deserialize("1583419435214 FINISHED no-files"), ExternalUploadFinishedEvent(1583419435214, "no-files"))
    assertEquals(deserialize("1583419435214 FINISHED"), ExternalUploadFinishedEvent(1583419435214, null))
    assertEquals(deserialize("1583419435214 FINISHED        "), ExternalUploadFinishedEvent(1583419435214, null))
    assertEquals(deserialize("1583419435214 FINISHED multi word message"), ExternalUploadFinishedEvent(1583419435214, "multi"))
  }

  fun test_parse_error_event() {
    assertEquals(
      deserialize("1583419435214 ERROR event.failed com.jetbrains.SomeException"),
      ExternalSystemErrorEvent(1583419435214, "event.failed", "com.jetbrains.SomeException")
    )
  }

  fun test_parse_finished_event_failed() {
    assertNull(deserialize("FINISHED"))
    assertNull(deserialize("FINISHED multi word message"))
    assertNull(deserialize("FINISHED 1583419435214 message"))
    assertNull(deserialize("FINISHED message"))
  }

  fun test_parse_send_event_failed() {
    assertNull(deserialize("1583419435214 SEND"))
    assertNull(deserialize("1583419435214 SEND 2"))
    assertNull(deserialize("1583419435214 SEND 2 3"))
    assertNull(deserialize("1583419435214 SEND 2 3 4 5"))
    assertNull(deserialize("1583419435214 SEND 2,3,5"))
    assertNull(deserialize("1583419435214 SEND,2,3,5"))
    assertNull(deserialize("SEND 2 3 4 5"))
    assertNull(deserialize("SEND 2 3 4"))
  }

  fun test_parse_error_event_failed() {
    assertNull(deserialize("1583419435214 ERROR"))
    assertNull(deserialize("1583419435214 ERROR event.failed"))
    assertNull(deserialize("ERROR event.failed com.jetbrains.SomeException"))
    assertNull(deserialize("1583419435214 ERROR event.failed com.jetbrains.SomeException more.messages"))
  }

  fun test_parse_unknown_event_failed() {
    assertNull(deserialize("SEN"))
    assertNull(deserialize("STARTEDD"))
    assertNull(deserialize("FINISH"))
    assertNull(deserialize("FAIL"))
    assertNull(deserialize("UNKNOWN"))
    assertNull(deserialize("UNKNOWN with-message"))
    assertNull(deserialize("UNKNOWN 1 23 4"))

    assertNull(deserialize("STARTED"))
    assertNull(deserialize("1583419435214 SEN"))
    assertNull(deserialize("1583419435214 STARTEDD"))
    assertNull(deserialize("1583419435214 FINISH"))
    assertNull(deserialize("1583419435214 FAIL"))
    assertNull(deserialize("1583419435214 UNKNOWN"))
    assertNull(deserialize("1583419435214 UNKNOWN with-message"))
    assertNull(deserialize("1583419435214 UNKNOWN 1 23 4"))
  }

  fun test_serialize_start_event() {
    assertEquals(serialize(ExternalUploadStartedEvent(1583419435214)), "1583419435214 STARTED")
  }

  fun test_serialize_send_event() {
    assertEquals(serialize(ExternalUploadSendEvent(1583419435214, 1, 2, 3)), "1583419435214 SEND 1 2 3")
    assertEquals(serialize(ExternalUploadSendEvent(1583419435214, 5, -1, 12)), "1583419435214 SEND 5 -1 12")
  }

  fun test_serialize_failed_event() {
    assertEquals(serialize(
      ExternalUploadFinishedEvent(1583419435214, "error-on-send")),
      "1583419435214 FINISHED error-on-send"
    )

    assertEquals(serialize(
      ExternalUploadFinishedEvent(1583419435214, "message")),
      "1583419435214 FINISHED message"
    )

    assertEquals(serialize(
      ExternalUploadFinishedEvent(1583419435214, "multi word message")),
      "1583419435214 FINISHED multi_word_message"
    )

    assertEquals(serialize(
      ExternalUploadFinishedEvent(1583419435214, "")),
      "1583419435214 FINISHED"
    )

    assertEquals(serialize(
      ExternalUploadFinishedEvent(1583419435214, null)),
      "1583419435214 FINISHED"
    )
  }

  fun test_serialize_error_event() {
    assertEquals(serialize(
      ExternalSystemErrorEvent(1583419435214, "loading.config.failed", "com.jetbrains.SomeException")),
      "1583419435214 ERROR loading.config.failed com.jetbrains.SomeException"
    )

    assertEquals(serialize(
      ExternalSystemErrorEvent(1583419435214, "loading", "SomeException")),
      "1583419435214 ERROR loading SomeException"
    )

    val ex = MockEventLogCustomException()
    assertEquals(serialize(
      ExternalSystemErrorEvent(1583419435214, "event.failed", ex)),
      "1583419435214 ERROR event.failed com.intellij.internal.statistics.uploader.MockEventLogCustomException"
    )
  }

  private fun doTestParseFile(fileText: String, vararg expectedEvents: ExternalSystemEvent) {
    val directory = FileUtil.createTempDirectory("idea-external-send-result", null)
    try {
      FileUtil.writeToFile(File(directory, "idea_statistics_uploader_events.log"), fileText)
      val actual = ExternalEventsLogger.parseEvents(directory)
      assertEquals(actual.size, expectedEvents.size)
      for ((index, expectedEvent) in expectedEvents.withIndex()) {
        assertEquals(actual[index], expectedEvent)
      }
    }
    finally {
      directory.deleteRecursively()
    }
  }

  fun test_parse_external_send_started_result_file() {
    doTestParseFile(
      "1583419435214 STARTED",
      ExternalUploadStartedEvent(1583419435214)
    )
  }

  fun test_parse_external_send_succeed_result_file() {
    doTestParseFile(
      "1583419435214 STARTED\n1583419435234 SEND 1 2 3",
      ExternalUploadStartedEvent(1583419435214), ExternalUploadSendEvent(1583419435234, 1, 2, 3)
    )
  }

  fun test_parse_external_send_failed_result_file() {
    doTestParseFile(
      "1583419435214 STARTED\n1583419435218 FINISHED error-on-send",
      ExternalUploadStartedEvent(1583419435214), ExternalUploadFinishedEvent(1583419435218, "error-on-send")
    )
  }

  fun test_parse_external_send_with_ending_lineseparator() {
    doTestParseFile(
      "1583419435218 STARTED\n",
      ExternalUploadStartedEvent(1583419435218)
    )
  }

  fun test_parse_external_send_with_multiple_lineseparators() {
    doTestParseFile(
      "1583419435218 STARTED\n\n\n",
      ExternalUploadStartedEvent(1583419435218)
    )
  }

  fun test_parse_external_send_with_multiple_events_including_invalid() {
    doTestParseFile(
      "1583419435218 STARTED\n1583419435219 UNKNOWN\n1583419435220 FINISHED error",
      ExternalUploadStartedEvent(1583419435218), ExternalUploadFinishedEvent(1583419435220, "error")
    )
  }

  fun test_parse_external_send_with_invalid_event() {
    doTestParseFile("UNKNOWN")
  }

  fun test_parse_external_send_started_without_timestamp() {
    doTestParseFile(
      "STARTED"
    )
  }

  fun test_parse_external_send_multiple_events_without_timestamp() {
    doTestParseFile(
      "STARTED\nSEND 1 2 3"
    )
  }

  fun test_parse_external_send_with_empty_file() {
    doTestParseFile("")
  }
}

private class MockEventLogCustomException : Exception()