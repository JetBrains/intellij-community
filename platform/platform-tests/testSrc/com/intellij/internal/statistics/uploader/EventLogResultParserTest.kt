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
    assertEquals(ExternalUploadStartedEvent(1583419425124), deserialize("1583419425124 STARTED"))
    assertEquals(ExternalUploadStartedEvent(1583317425114), deserialize("1583317425114 STARTED"))
  }

  fun test_parse_send_event() {
    assertEquals(ExternalUploadSendEvent(1583419435214, 2, 1, 10, emptyList(), emptyList()), deserialize("1583419435214 SEND 2 1 10"))
    assertEquals(ExternalUploadSendEvent(1583419435214, 6, 0, 15, emptyList(), emptyList()), deserialize("1583419435214 SEND 6 0 15"))
    assertEquals(ExternalUploadSendEvent(1583419435214, 2, -1, 5, emptyList(), emptyList()), deserialize("1583419435214 SEND 2 a 5"))
    assertEquals(ExternalUploadSendEvent(1583419435214, -1, -1, -1, emptyList(), emptyList()), deserialize("1583419435214 SEND a b b"))
    assertEquals(ExternalUploadSendEvent(1583419435214, -1, 34, 50, emptyList(), emptyList()), deserialize("1583419435214 SEND 12a 34 50"))
  }

  fun test_parse_send_event_with_files_hashes() {
    assertEquals(ExternalUploadSendEvent(1583419435214, 2, 1, 10, listOf("path_to_file1", "path_to_file2"), emptyList()),
                 deserialize("1583419435214 SEND 2 1 10 [cGF0aF90b19maWxlMQ==,cGF0aF90b19maWxlMg==]"))
    assertEquals(ExternalUploadSendEvent(1583419435214, 2, 1, 10, emptyList(), emptyList()), deserialize("1583419435214 SEND 2 1 10 []"))
  }

  fun test_parse_send_event_with_errors() {
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 1, 1, 10, emptyList(), emptyList()),
      deserialize("1583419435214 SEND 1 1 10 [] []")
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 1, 1, 10, emptyList(), arrayListOf(1)),
      deserialize("1583419435214 SEND 1 1 10 [] [1]")
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 1, 2, 10, emptyList(), arrayListOf(1, 5)),
      deserialize("1583419435214 SEND 1 2 10 [] [1,5]")
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 1, 3, 10, emptyList(), arrayListOf(1, 5, 400)),
      deserialize("1583419435214 SEND 1 3 10 [] [1,5,400]")
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 1, 3, 10, emptyList(), emptyList()),
      deserialize("1583419435214 SEND 1 3 10 [] 400")
    )
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 1, 3, 10, emptyList(), emptyList()),
      deserialize("1583419435214 SEND 1 3 10 123 400")
    )
  }

  fun test_parse_send_event_with_pathes_and_errors() {
    assertEquals(
      ExternalUploadSendEvent(1583419435214, 1, 3, 10, arrayListOf("path_to_file1"), arrayListOf(1, 5, 400)),
      deserialize("1583419435214 SEND 1 3 10 [cGF0aF90b19maWxlMQ==] [1,5,400]")
    )
  }

  fun test_parse_finished_event() {
    assertEquals(ExternalUploadFinishedEvent(1583419435214, "no-arguments"), deserialize("1583419435214 FINISHED no-arguments"))
    assertEquals(ExternalUploadFinishedEvent(1583419435214, "no-files"), deserialize("1583419435214 FINISHED no-files"))
    assertEquals(ExternalUploadFinishedEvent(1583419435214, null), deserialize("1583419435214 FINISHED"))
    assertEquals(ExternalUploadFinishedEvent(1583419435214, null), deserialize("1583419435214 FINISHED        "))
    assertEquals(ExternalUploadFinishedEvent(1583419435214, "multi"), deserialize("1583419435214 FINISHED multi word message"))
  }

  fun test_parse_error_event() {
    assertEquals(
      ExternalSystemErrorEvent(1583419435214, "event.failed", "com.jetbrains.SomeException"),
      deserialize("1583419435214 ERROR event.failed com.jetbrains.SomeException")
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
    assertNull(deserialize("1583419435214 SEND 2 3 4 5 6 7"))
    assertNull(deserialize("1583419435214 SEND 2,3,5"))
    assertNull(deserialize("1583419435214 SEND,2,3,5"))
    assertNull(deserialize("SEND 2 3 4 5"))
    assertNull(deserialize("SEND 2 3 4"))
  }

  fun test_parse_send_event_if_files_hashes_incorrect() {
    assertEquals(ExternalUploadSendEvent(1583419435214, 2, 1, 10, emptyList(), emptyList()),
                 deserialize("1583419435214 SEND 2 1 10 qwerty"))
    assertEquals(ExternalUploadSendEvent(1583419435214, 2, 1, 10, emptyList(), emptyList()), deserialize("1583419435214 SEND 2 1 10 [']"))
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
    assertEquals("1583419435214 STARTED", serialize(ExternalUploadStartedEvent(1583419435214)))
  }

  fun test_serialize_send_event() {
    assertEquals(
      "1583419435214 SEND 1 2 3 [cGF0aF90b19maWxl] []",
      serialize(ExternalUploadSendEvent(1583419435214, 1, 2, 3, listOf("path_to_file"), emptyList()))
    )
    assertEquals(
      "1583419435214 SEND 2 -1 12 [cGF0aF90b19maWxlMQ==,cGF0aF90b19maWxlMg==] []",
      serialize(ExternalUploadSendEvent(1583419435214, 2, -1, 12, listOf("path_to_file1", "path_to_file2"), emptyList()))
    )
  }

  fun test_serialize_send_event_without_path() {
    assertEquals(
      "1583419435214 SEND 0 2 3 [] []",
      serialize(ExternalUploadSendEvent(1583419435214, 0, 2, 3, emptyList(), emptyList()))
    )
  }

  fun test_serialize_send_event_without_path_but_with_errors() {
    assertEquals(
      "1583419435214 SEND 0 3 3 [] [400,1,5]",
      serialize(ExternalUploadSendEvent(1583419435214, 0, 3, 3, emptyList(), arrayListOf(400, 1, 5)))
    )
    assertEquals(
      "1583419435214 SEND 0 2 3 [] [400,50]",
      serialize(ExternalUploadSendEvent(1583419435214, 0, 2, 3, emptyList(), arrayListOf(400, 50)))
    )
    assertEquals(
      "1583419435214 SEND 0 1 3 [] [-10]",
      serialize(ExternalUploadSendEvent(1583419435214, 0, 1, 3, emptyList(), arrayListOf(-10)))
    )
  }

  fun test_serialize_send_event_with_errors() {
    assertEquals(
      "1583419435214 SEND 1 2 3 [cGF0aF90b19maWxl] [50,400]",
      serialize(ExternalUploadSendEvent(1583419435214, 1, 2, 3, listOf("path_to_file"), arrayListOf(50, 400)))
    )
    assertEquals(
      "1583419435214 SEND 2 -1 12 [cGF0aF90b19maWxlMQ==,cGF0aF90b19maWxlMg==] [400]",
      serialize(ExternalUploadSendEvent(1583419435214, 2, -1, 12, listOf("path_to_file1", "path_to_file2"), arrayListOf(400)))
    )
  }

  fun test_serialize_failed_event() {
    assertEquals("1583419435214 FINISHED error-on-send",
                 serialize(ExternalUploadFinishedEvent(1583419435214, "error-on-send"))
    )

    assertEquals("1583419435214 FINISHED message",
                 serialize(ExternalUploadFinishedEvent(1583419435214, "message"))
    )

    assertEquals("1583419435214 FINISHED multi_word_message",
                 serialize(ExternalUploadFinishedEvent(1583419435214, "multi word message"))
    )

    assertEquals("1583419435214 FINISHED",
                 serialize(ExternalUploadFinishedEvent(1583419435214, ""))
    )

    assertEquals("1583419435214 FINISHED",
                 serialize(ExternalUploadFinishedEvent(1583419435214, null))
    )
  }

  fun test_serialize_error_event() {
    assertEquals("1583419435214 ERROR loading.config.failed com.jetbrains.SomeException",
                 serialize(ExternalSystemErrorEvent(1583419435214, "loading.config.failed", "com.jetbrains.SomeException"))
    )

    assertEquals("1583419435214 ERROR loading SomeException",
                 serialize(ExternalSystemErrorEvent(1583419435214, "loading", "SomeException"))
    )

    val ex = MockEventLogCustomException()
    assertEquals("1583419435214 ERROR event.failed com.intellij.internal.statistics.uploader.MockEventLogCustomException",
                 serialize(ExternalSystemErrorEvent(1583419435214, "event.failed", ex))
    )
  }

  private fun doTestParseFile(fileText: String, vararg expectedEvents: ExternalSystemEvent) {
    val directory = FileUtil.createTempDirectory("idea-external-send-result", null)
    try {
      FileUtil.writeToFile(File(directory, "idea_statistics_uploader_events.log"), fileText)
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

  fun test_parse_external_send_started_result_file() {
    doTestParseFile(
      "1583419435214 STARTED",
      ExternalUploadStartedEvent(1583419435214)
    )
  }

  fun test_parse_external_send_succeed_result_file() {
    doTestParseFile(
      "1583419435214 STARTED\n1583419435234 SEND 1 2 3",
      ExternalUploadStartedEvent(1583419435214), ExternalUploadSendEvent(1583419435234, 1, 2, 3, emptyList(), emptyList())
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