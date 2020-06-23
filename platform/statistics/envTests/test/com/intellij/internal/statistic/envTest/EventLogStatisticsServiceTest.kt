// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.envTest

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.intellij.internal.statistic.connect.StatisticsResult
import com.intellij.internal.statistic.eventLog.*
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.readText
import junit.framework.TestCase
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

internal class EventLogStatisticsServiceTest : StatisticsUploaderBaseTest() {
  private val recorderId = "FUS"
  private val productCode = "IC"
  private val gson = GsonBuilder().registerTypeAdapter(LogEvent::class.java, LogEventJsonDeserializer()).create()

  fun testSend() {
    val logText = Paths.get(getTestDataPath()).resolve("log1.txt").readText()
    val requests = doTest(listOf(createTempFile("testLogFile.log", logText)))

    TestCase.assertEquals(1, requests.size)
    val logEventRecordRequest = requests.first()
    TestCase.assertEquals(recorderId, logEventRecordRequest.recorder)
    TestCase.assertEquals(productCode, logEventRecordRequest.product)
    TestCase.assertEquals(1, logEventRecordRequest.records.size)
    val events = logEventRecordRequest.records.first().events
    TestCase.assertEquals(1, events.size)
    val expected = gson.fromJson(logText, LogEvent::class.java)
    TestCase.assertEquals(expected, events.first())
  }

  fun testSendMultipleLogFiles() {
    val logFiles = listOf(
      createLogFile("testLogFile1.log", "log1.txt"),
      createLogFile("testLogFile2.log", "log2.txt")
    )
    val requests = doTest(logFiles)
    TestCase.assertEquals(logFiles.size, requests.size)
  }

  fun testSendIfSettingsUnreachable() {
    val logFile = createLogFile("testLogFile.log", "log1.txt")
    val service = createEventLogStatisticsService(listOf(logFile), settingsResponseFile = "non-existent.php")
    val resultHolder = ResultHolder()
    val result = service.send(resultHolder)
    TestCase.assertEquals(result.description, StatisticsResult.ResultCode.ERROR_IN_CONFIG, result.code)
    TestCase.assertEquals(0, resultHolder.succeed)
    TestCase.assertTrue(logFile.exists())
  }

  fun testSendIfServerUnreachable() {
    createSettingsResponseScript(sendResponseFile = "non-existent.php")
    val logFile = createLogFile("testLogFile.log", "log1.txt")
    val service = createEventLogStatisticsService(listOf(logFile))

    val resultHolder = ResultHolder()
    val result = service.send(resultHolder)
    TestCase.assertEquals(result.description, StatisticsResult.ResultCode.SENT_WITH_ERRORS, result.code)
    TestCase.assertEquals(0, resultHolder.succeed)
    TestCase.assertTrue(logFile.exists())
  }

  fun testSendIfWhitelistUnreachable() {
    createSettingsResponseScript(whitelistResponseFile = "non-existent.php")
    val logFile = createLogFile("testLogFile.log", "log1.txt")
    val service = createEventLogStatisticsService(listOf(logFile))
    val resultHolder = ResultHolder()
    val result = service.send(resultHolder)
    TestCase.assertEquals(result.description, StatisticsResult.ResultCode.SENT_WITH_ERRORS, result.code)
    TestCase.assertEquals(0, resultHolder.succeed)
    TestCase.assertTrue(!logFile.exists())
  }

  fun testNotSendIfSendDisabled() {
    val logFile = createLogFile("testLogFile.log", "log1.txt")
    val service = createEventLogStatisticsService(listOf(logFile), sendEnabled = false)
    val resultHolder = ResultHolder()
    val result = service.send(resultHolder)
    TestCase.assertEquals(result.description, StatisticsResult.ResultCode.NOTHING_TO_SEND, result.code)
    TestCase.assertEquals(0, resultHolder.succeed)
    TestCase.assertTrue(!logFile.exists())
  }

  private fun createLogFile(targetName: String, sourceName: String): File {
    return createTempFile(targetName, Paths.get(getTestDataPath()).resolve(sourceName).readText())
  }

  private fun doTest(logFiles: List<File>): List<LogEventRecordRequest> {
    createSettingsResponseScript()
    val service = createEventLogStatisticsService(logFiles)
    val resultHolder = ResultHolder()

    val result = service.send(resultHolder)
    TestCase.assertEquals(result.description, StatisticsResult.ResultCode.SEND, result.code)
    for (logFile in logFiles) {
      TestCase.assertTrue(!logFile.exists())
    }

    return resultHolder.successContents.map {
      val jsonObject = gson.fromJson(it, JsonObject::class.java)
      TestCase.assertEquals(jsonObject["method"].asString, "POST")
      gson.fromJson(jsonObject["body"].asString, LogEventRecordRequest::class.java)
    }
  }

  private fun createSettingsResponseScript(sendResponseFile: String = "dump-request.php", whitelistResponseFile: String = "") {
    val sendUrl = container.getBaseUrl(sendResponseFile).toString()
    val whitelistUrl = container.getBaseUrl(whitelistResponseFile).toString()
    val settings = """<service url="$sendUrl" percent-traffic="100" white-list-service="$whitelistUrl"/>"""
    FileUtil.writeToFile(Paths.get(tmpLocalRoot).resolve("settings.php").toFile(), settings)
  }

  private fun createEventLogStatisticsService(logFiles: List<File>,
                                              settingsResponseFile: String = "settings.php",
                                              sendEnabled: Boolean = true): EventLogStatisticsService {
    val applicationInfo = TestApplicationInfo(recorderId, productCode, container.getBaseUrl(settingsResponseFile).toString())
    return EventLogStatisticsService(
      DeviceConfiguration(EventLogConfiguration.deviceId, EventLogConfiguration.bucket),
      TestEventLogRecorderConfig(recorderId, logFiles, sendEnabled),
      EventLogSendListener { _, _, _ -> Unit },
      EventLogUploadSettingsService(recorderId, applicationInfo)
    )
  }

  private class TestApplicationInfo(recorderId: String,
                                    val product: String,
                                    val settingsUrl: String) : EventLogInternalApplicationInfo(recorderId, true) {
    override fun getProductCode(): String = product
    override fun getTemplateUrl(): String = settingsUrl
  }

  private class TestEventLogRecorderConfig(recorderId: String,
                                           logFiles: List<File>,
                                           val sendEnabled: Boolean = true) : EventLogInternalRecorderConfig(recorderId) {
    private val evenLogFilesProvider = object : EventLogFilesProvider {
      override fun getLogFilesDir(): Path? = null

      override fun getLogFiles(): List<EventLogFile> = logFiles.map { EventLogFile(it) }
    }

    override fun isSendEnabled(): Boolean = sendEnabled

    override fun getLogFilesProvider(): EventLogFilesProvider = evenLogFilesProvider
  }

  private class ResultHolder : EventLogResultDecorator {
    var failed = 0
    var succeed = 0
    val successContents = mutableListOf<String>()

    override fun onSucceed(request: LogEventRecordRequest, content: String) {
      succeed++
      successContents.add(content)
    }

    override fun onFailed(request: LogEventRecordRequest?, content: String?) {
      failed++
    }

    override fun onFinished(): StatisticsResult {
      val total = succeed + failed
      if (total == 0) {
        return StatisticsResult(StatisticsResult.ResultCode.NOTHING_TO_SEND, "No files to upload.")
      }
      else if (failed > 0) {
        return StatisticsResult(StatisticsResult.ResultCode.SENT_WITH_ERRORS, "Uploaded $succeed out of $total files.")
      }
      return StatisticsResult(StatisticsResult.ResultCode.SEND, "Uploaded $succeed files.")
    }
  }
}