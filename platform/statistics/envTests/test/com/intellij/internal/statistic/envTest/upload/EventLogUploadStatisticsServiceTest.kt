// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.envTest.upload

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.intellij.internal.statistic.connect.StatisticsResult
import com.intellij.internal.statistic.connect.StatisticsResult.ResultCode
import com.intellij.internal.statistic.envTest.StatisticsServiceBaseTest
import com.intellij.internal.statistic.eventLog.*
import com.intellij.util.io.readText
import junit.framework.TestCase
import java.io.File
import java.nio.file.Paths

internal class EventLogUploadStatisticsServiceTest : StatisticsServiceBaseTest() {
  private val gson = GsonBuilder().registerTypeAdapter(LogEvent::class.java, LogEventJsonDeserializer()).create()

  fun testSend() {
    val logText = Paths.get(getTestDataPath()).resolve("log1.txt").readText()
    val requests = doTest(listOf(createTempFile("testLogFile.log", logText)))

    TestCase.assertEquals(1, requests.size)
    val logEventRecordRequest = requests.first()
    TestCase.assertEquals(RECORDER_ID, logEventRecordRequest.recorder)
    TestCase.assertEquals(PRODUCT_CODE, logEventRecordRequest.product)
    TestCase.assertEquals(1, logEventRecordRequest.records.size)

    val events = logEventRecordRequest.records.first().events
    TestCase.assertEquals(1, events.size)
    val expected = gson.fromJson(logText, LogEvent::class.java)
    TestCase.assertEquals(expected, events.first())
  }

  fun testSendMultipleLogFiles() {
    val logFiles = listOf(
      newLogFile("testLogFile1.log", "log1.txt"),
      newLogFile("testLogFile2.log", "log2.txt")
    )
    val requests = doTest(logFiles)
    TestCase.assertEquals(logFiles.size, requests.size)
  }

  fun testSendIfSettingsUnreachable() {
    val logFile = newLogFile("testLogFile.log", "log1.txt")
    val service = newSendService(container, listOf(logFile), settingsResponseFile = "non-existent.php")
    doTestFailed(EventLogConfigBuilder(container, tmpLocalRoot), service, ResultCode.ERROR_IN_CONFIG)
    TestCase.assertTrue(logFile.exists())
  }

  fun testNoSendUrlSpecified() {
    val config = EventLogConfigBuilder(container, tmpLocalRoot).withSendUrlPath(null)
    val logFile = newLogFile("testLogFile.log", "log1.txt")
    doTestFailed(config, listOf(logFile), ResultCode.ERROR_IN_CONFIG)
    TestCase.assertTrue(logFile.exists())
  }

  fun testSendIfServerUnreachable() {
      val config = EventLogConfigBuilder(container, tmpLocalRoot).withSendUrlPath("non-existent.php")
    val logFile = newLogFile("testLogFile.log", "log1.txt")
    doTestSendWithError(config, logFile, true)
  }

  fun testFailedSendWithFormatRejected() {
    val config = EventLogConfigBuilder(container, tmpLocalRoot).withSendUrlPath("bad-request-code.php")
    val logFile = newLogFile("testLogFile.log", "log1.txt")
    doTestSendWithError(config, logFile)
  }

  fun testFailedSendWithUnknownHost() {
    val config = EventLogConfigBuilder(container, tmpLocalRoot)
      .withSendHost("http://not.existing.host")

    val logFile = newLogFile("testLogFile.log", "log1.txt")
    doTestSendWithError(config, logFile, true)
  }

  fun testFailedSendWithErrorInRequest() {
    val logFile = newLogFile("testLogFile.log", "invalidLog.txt")
    doTestSendWithError(EventLogConfigBuilder(container, tmpLocalRoot), logFile)
  }

  fun testFailedSendWithNoFilesToSend() {
    val config = EventLogConfigBuilder(container, tmpLocalRoot)
    doTestFailed(config, emptyList(), ResultCode.NOTHING_TO_SEND)
  }

  fun testFailedSendWithEmptyBucketFilter() {
    val config = EventLogConfigBuilder(container, tmpLocalRoot).withToBucket(0)
    val logFile = newLogFile("testLogFile.log", "log1.txt")
    doTestFailed(config, listOf(logFile), ResultCode.SENT_WITH_ERRORS)
    TestCase.assertFalse(logFile.exists())
  }

  fun testFailedSendWithNoPermitted() {
    val config = EventLogConfigBuilder(container, tmpLocalRoot).withProductVersion("2020.2")
    val logFile = newLogFile("testLogFile.log", "log1.txt")
    doTestFailed(config, listOf(logFile), ResultCode.NOT_PERMITTED_SERVER)
    TestCase.assertFalse(logFile.exists())
  }

  fun testSendIfWhitelistUnreachable() {
    val config = EventLogConfigBuilder(container, tmpLocalRoot).withWhitelistUrlPath("non-existent")
    val logFile = newLogFile("testLogFile.log", "log1.txt")
    doTestSendWithError(config, logFile)
  }

  fun testNotSendIfSendDisabled() {
    val logFile = newLogFile("testLogFile.log", "log1.txt")
    val service = newSendService(container, listOf(logFile), sendEnabled = false)
    doTestFailed(EventLogConfigBuilder(container, tmpLocalRoot), service, ResultCode.NOTHING_TO_SEND)
    TestCase.assertFalse(logFile.exists())
  }

  private fun newLogFile(targetName: String, sourceName: String): File {
    return createTempFile(targetName, Paths.get(getTestDataPath()).resolve(sourceName).readText())
  }

  private fun doTestSendWithError(config: EventLogConfigBuilder, logFile: File, fileExists: Boolean = false) {
    doTestFailed(config, listOf(logFile), ResultCode.SENT_WITH_ERRORS)
    TestCase.assertEquals(fileExists, logFile.exists())
  }

  private fun doTestFailed(config: EventLogConfigBuilder, logFile: List<File>, expected: ResultCode) {
    doTestFailed(config, newSendService(container, logFile), expected)
  }

  private fun doTestFailed(config: EventLogConfigBuilder, service: EventLogStatisticsService, expected: ResultCode) {
    config.create()
    val resultHolder = ResultHolder()
    val result = service.send(resultHolder)
    TestCase.assertEquals(result.description, expected, result.code)
    TestCase.assertEquals(0, resultHolder.getSucceed())
  }

  private fun doTest(logFiles: List<File>): List<LogEventRecordRequest> {
    EventLogConfigBuilder(container, tmpLocalRoot).create()
    val service = newSendService(container, logFiles)
    val resultHolder = ResultHolder()

    val result = service.send(resultHolder)
    TestCase.assertEquals(result.description, ResultCode.SEND, result.code)
    for (logFile in logFiles) {
      TestCase.assertFalse(logFile.exists())
      TestCase.assertTrue(resultHolder.successfullySentFiles.contains(logFile.absolutePath))
    }

    return resultHolder.successContents.map {
      val jsonObject = gson.fromJson(it, JsonObject::class.java)
      TestCase.assertEquals(jsonObject["method"].asString, "POST")
      gson.fromJson(jsonObject["body"].asString, LogEventRecordRequest::class.java)
    }
  }

  private class ResultHolder : EventLogResultDecorator {
    var failed = 0
    val successfullySentFiles= mutableListOf<String>()
    val successContents = mutableListOf<String>()

    override fun onSucceed(request: LogEventRecordRequest, content: String, logPath: String) {
      successfullySentFiles.add(logPath)
      successContents.add(content)
    }

    override fun onFailed(request: LogEventRecordRequest?, content: String?) {
      failed++
    }

    override fun onFinished(): StatisticsResult {
      val succeed = getSucceed()
      val total = succeed + failed
      if (total == 0) {
        return StatisticsResult(ResultCode.NOTHING_TO_SEND, "No files to upload.")
      }
      else if (failed > 0) {
        return StatisticsResult(ResultCode.SENT_WITH_ERRORS, "Uploaded $succeed out of $total files.")
      }
      return StatisticsResult(ResultCode.SEND, "Uploaded $succeed files.")
    }

    fun getSucceed(): Int = successfullySentFiles.size
  }
}