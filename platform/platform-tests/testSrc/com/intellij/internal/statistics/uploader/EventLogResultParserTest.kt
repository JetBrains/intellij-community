// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.uploader

import com.intellij.internal.statistic.uploader.events.*
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase
import java.io.File

class EventLogResultParserTest : UsefulTestCase() {

  fun test_parse_start_event() {
    assertEquals(ExternalUploadEventSerializer.deserialize("1583419425124 STARTED"), ExternalUploadStartedEvent(1583419425124))
    assertEquals(ExternalUploadEventSerializer.deserialize("1583317425114 STARTED"), ExternalUploadStartedEvent(1583317425114))
  }

  fun test_parse_send_event() {
    assertEquals(ExternalUploadEventSerializer.deserialize("1583419435214 SEND 2 1 10"), ExternalUploadSendEvent(1583419435214, 2, 1, 10))
    assertEquals(ExternalUploadEventSerializer.deserialize("1583419435214 SEND 6 0 15"), ExternalUploadSendEvent(1583419435214, 6, 0, 15))
    assertEquals(ExternalUploadEventSerializer.deserialize("1583419435214 SEND 2 a 5"), ExternalUploadSendEvent(1583419435214, 2, -1, 5))
    assertEquals(ExternalUploadEventSerializer.deserialize("1583419435214 SEND a b b"), ExternalUploadSendEvent(1583419435214, -1, -1, -1))
    assertEquals(ExternalUploadEventSerializer.deserialize("1583419435214 SEND 12a 34 50"), ExternalUploadSendEvent(1583419435214, -1, 34, 50))
  }

  fun test_parse_finished_event() {
    assertEquals(ExternalUploadEventSerializer.deserialize("1583419435214 FINISHED no-arguments"), ExternalUploadFinishedEvent(1583419435214, "no-arguments"))
    assertEquals(ExternalUploadEventSerializer.deserialize("1583419435214 FINISHED no-files"), ExternalUploadFinishedEvent(1583419435214, "no-files"))
    assertEquals(ExternalUploadEventSerializer.deserialize("1583419435214 FINISHED"), ExternalUploadFinishedEvent(1583419435214, null))
    assertEquals(ExternalUploadEventSerializer.deserialize("1583419435214 FINISHED        "), ExternalUploadFinishedEvent(1583419435214, null))
    assertEquals(ExternalUploadEventSerializer.deserialize("1583419435214 FINISHED multi word message"), ExternalUploadFinishedEvent(1583419435214, "multi"))
  }

  fun test_parse_finished_event_failed() {
    assertNull(ExternalUploadEventSerializer.deserialize("FINISHED"))
    assertNull(ExternalUploadEventSerializer.deserialize("FINISHED multi word message"))
    assertNull(ExternalUploadEventSerializer.deserialize("FINISHED 1583419435214 message"))
    assertNull(ExternalUploadEventSerializer.deserialize("FINISHED message"))
  }

  fun test_parse_send_event_failed() {
    assertNull(ExternalUploadEventSerializer.deserialize("1583419435214 SEND"))
    assertNull(ExternalUploadEventSerializer.deserialize("1583419435214 SEND 2"))
    assertNull(ExternalUploadEventSerializer.deserialize("1583419435214 SEND 2 3"))
    assertNull(ExternalUploadEventSerializer.deserialize("1583419435214 SEND 2 3 4 5"))
    assertNull(ExternalUploadEventSerializer.deserialize("1583419435214 SEND 2,3,5"))
    assertNull(ExternalUploadEventSerializer.deserialize("1583419435214 SEND,2,3,5"))
    assertNull(ExternalUploadEventSerializer.deserialize("SEND 2 3 4 5"))
    assertNull(ExternalUploadEventSerializer.deserialize("SEND 2 3 4"))
  }

  fun test_parse_unknown_event_failed() {
    assertNull(ExternalUploadEventSerializer.deserialize("SEN"))
    assertNull(ExternalUploadEventSerializer.deserialize("STARTEDD"))
    assertNull(ExternalUploadEventSerializer.deserialize("FINISH"))
    assertNull(ExternalUploadEventSerializer.deserialize("FAIL"))
    assertNull(ExternalUploadEventSerializer.deserialize("UNKNOWN"))
    assertNull(ExternalUploadEventSerializer.deserialize("UNKNOWN with-message"))
    assertNull(ExternalUploadEventSerializer.deserialize("UNKNOWN 1 23 4"))

    assertNull(ExternalUploadEventSerializer.deserialize("STARTED"))
    assertNull(ExternalUploadEventSerializer.deserialize("1583419435214 SEN"))
    assertNull(ExternalUploadEventSerializer.deserialize("1583419435214 STARTEDD"))
    assertNull(ExternalUploadEventSerializer.deserialize("1583419435214 FINISH"))
    assertNull(ExternalUploadEventSerializer.deserialize("1583419435214 FAIL"))
    assertNull(ExternalUploadEventSerializer.deserialize("1583419435214 UNKNOWN"))
    assertNull(ExternalUploadEventSerializer.deserialize("1583419435214 UNKNOWN with-message"))
    assertNull(ExternalUploadEventSerializer.deserialize("1583419435214 UNKNOWN 1 23 4"))
  }

  fun test_serialize_start_event() {
    TestCase.assertEquals(ExternalUploadEventSerializer.serialize(ExternalUploadStartedEvent(1583419435214)), "1583419435214 STARTED")
  }

  fun test_serialize_send_event() {
    TestCase.assertEquals(ExternalUploadEventSerializer.serialize(ExternalUploadSendEvent(1583419435214, 1, 2, 3)), "1583419435214 SEND 1 2 3")
    TestCase.assertEquals(ExternalUploadEventSerializer.serialize(ExternalUploadSendEvent(1583419435214, 5, -1, 12)), "1583419435214 SEND 5 -1 12")
  }

  fun test_serialize_failed_event() {
    TestCase.assertEquals(ExternalUploadEventSerializer.serialize(ExternalUploadFinishedEvent(1583419435214, "error-on-send")), "1583419435214 FINISHED error-on-send")
    TestCase.assertEquals(ExternalUploadEventSerializer.serialize(ExternalUploadFinishedEvent(1583419435214, "message")), "1583419435214 FINISHED message")
    TestCase.assertEquals(ExternalUploadEventSerializer.serialize(ExternalUploadFinishedEvent(1583419435214, "multi word message")), "1583419435214 FINISHED multi_word_message")
    TestCase.assertEquals(ExternalUploadEventSerializer.serialize(ExternalUploadFinishedEvent(1583419435214, "")), "1583419435214 FINISHED")
    TestCase.assertEquals(ExternalUploadEventSerializer.serialize(ExternalUploadFinishedEvent(1583419435214, null)), "1583419435214 FINISHED")
  }

  private fun doTestParseFile(fileText: String, vararg expectedEvents: ExternalUploadEvent) {
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