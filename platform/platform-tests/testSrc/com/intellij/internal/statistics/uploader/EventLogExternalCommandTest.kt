// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.uploader

import com.intellij.internal.statistic.eventLog.*
import com.intellij.internal.statistic.eventLog.uploader.EventLogExternalUploader
import com.intellij.internal.statistic.uploader.EventLogExternalSendConfig
import com.intellij.internal.statistic.uploader.EventLogUploaderCliParser
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions.*
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import java.io.File

class EventLogExternalCommandTest : BasePlatformTestCase() {

  private fun createCommand(configs: List<EventLogSendConfig>,
                            classPath: String = "/test/path1:/test/path2",
                            tempDir: File = File(PathManager.getTempPath(), "statistics-uploader")): Array<out String> {
    val appInfo = EventLogInternalApplicationInfo(false, false)
    return EventLogExternalUploader.createExternalUploadCommand(appInfo, configs, classPath, tempDir)
  }

  fun test_create_command_one_recorder() {
    val command = createCommand(listOf(TestEventLogSendConfig(
      "ABC", "test-device-id", 10,
      MachineId("test-machine-id", 15),
      false, listOf("/path/to/log/file")
    )))

    val options = EventLogUploaderCliParser.parseOptions(command)
    TestCase.assertEquals("ABC", options[RECORDERS_OPTION])

    TestCase.assertEquals("test-device-id", options[DEVICE_OPTION + "abc"])
    TestCase.assertEquals("10", options[BUCKET_OPTION + "abc"])
    TestCase.assertEquals("test-machine-id", options[MACHINE_ID_OPTION + "abc"])
    TestCase.assertEquals("15", options[ID_REVISION_OPTION + "abc"])
    TestCase.assertEquals("/path/to/log/file", options[LOGS_OPTION + "abc"])
  }

  fun test_create_command_multiple_recorders() {
    val command = createCommand(
      listOf(
        TestEventLogSendConfig(
          "ABC", "test-device-id", 10, MachineId("test-machine-id", 15),
          false, listOf("/path/to/log/file")
        ),
        TestEventLogSendConfig(
          "DEF", "another-device-id", 65, MachineId("another-machine-id", 98),
          false, listOf("/another/path/to/log")
        )
      )
    )

    val options = EventLogUploaderCliParser.parseOptions(command)
    TestCase.assertEquals("ABC;DEF", options[RECORDERS_OPTION])

    TestCase.assertEquals("test-device-id", options[DEVICE_OPTION + "abc"])
    TestCase.assertEquals("10", options[BUCKET_OPTION + "abc"])
    TestCase.assertEquals("test-machine-id", options[MACHINE_ID_OPTION + "abc"])
    TestCase.assertEquals("15", options[ID_REVISION_OPTION + "abc"])
    TestCase.assertEquals("/path/to/log/file", options[LOGS_OPTION + "abc"])

    TestCase.assertEquals("another-device-id", options[DEVICE_OPTION + "def"])
    TestCase.assertEquals("65", options[BUCKET_OPTION + "def"])
    TestCase.assertEquals("another-machine-id", options[MACHINE_ID_OPTION + "def"])
    TestCase.assertEquals("98", options[ID_REVISION_OPTION + "def"])
    TestCase.assertEquals("/another/path/to/log", options[LOGS_OPTION + "def"])
  }

  private fun doTestParse(vararg configs: TestEventLogSendConfig) {
    val logsToDelete = arrayListOf<File>()
    try {
      val logDir = FileUtil.createTempDirectory("event-log-command-test", "", false)
      logsToDelete.add(logDir)
      for (config in configs) {
        config.createLogFiles(logDir)
      }


      val command = createCommand(configs.toList())
      val actualConfigs = EventLogExternalSendConfig.parseSendConfigurations(
        EventLogUploaderCliParser.parseOptions(command)) { error, recorder ->
        TestCase.assertTrue("Failed parsing '$recorder': $error", false)
      }

      TestCase.assertEquals(configs.size, actualConfigs.size)
      for (expected in configs) {
        val actual = actualConfigs.find { it.getRecorderId() == expected.getRecorderId() }
        TestCase.assertEquals(expected.getDeviceId(), actual!!.getDeviceId())
        TestCase.assertEquals(expected.getBucket(), actual.getBucket())
        TestCase.assertEquals(expected.getMachineId(), actual.getMachineId())

        TestCase.assertEquals(
          expected.getFilesToSendProvider().getFilesToSend(),
          actual.getFilesToSendProvider().getFilesToSend()
        )
      }
    }
    finally {
      for (files in logsToDelete) {
        FileUtil.delete(files.toPath())
      }
    }
  }

  fun test_send_command_parse_one_recorder() {
    doTestParse(
      TestEventLogSendConfig(
        "ABC", "test-device-id", 10, MachineId("test-machine-id", 15), true, listOf("abc-log-file")
      )
    )
  }

  fun test_send_command_parse_multiple_log_files() {
    doTestParse(
      TestEventLogSendConfig(
        "ABC", "test-device-id", 10, MachineId("test-machine-id", 15),
        true, listOf("abc-log-file", "abc-second-log-file", "abc-third-log-file")
      )
    )
  }

  fun test_send_command_parse_without_log_files() {
    doTestParse(
      TestEventLogSendConfig(
        "ABC", "test-device-id", 10, MachineId("test-machine-id", 15), true, listOf()
      )
    )
  }

  fun test_send_command_parse_multiple_recorders() {
    doTestParse(
      TestEventLogSendConfig(
        "ABC", "test-device-id", 10, MachineId("test-machine-id", 15), true, listOf("abc-log-file")
      ),
      TestEventLogSendConfig(
        "DEF", "another-device-id", 65, MachineId("another-machine-id", 98), true, listOf("def-log-file")
      )
    )
  }
}

private class TestEventLogSendConfig(recorderId: String,
                                     deviceId: String,
                                     bucket: Int,
                                     machineId: MachineId,
                                     val withPhysicalFiles: Boolean = false,
                                     val fileNames: List<String> = emptyList())
  : EventLogExternalSendConfig(recorderId, deviceId, bucket, machineId, fileNames, true) {
  private val files = arrayListOf<File>()

  private val evenLogFilesProvider = object : FilesToSendProvider {
    override fun getFilesToSend(): List<EventLogFile> {
      return if (withPhysicalFiles) {
        files.map { EventLogFile(it) }
      }
      else {
        fileNames.map { EventLogFile(File(it)) }
      }
    }
  }

  fun createLogFiles(logDir: File) {
    for (logFile in fileNames) {
      files.add(FileUtil.createTempFile(logDir, logFile, ".log", true))
    }
  }

  override fun getFilesToSendProvider(): FilesToSendProvider = evenLogFilesProvider

  override fun isSendEnabled(): Boolean = true
}