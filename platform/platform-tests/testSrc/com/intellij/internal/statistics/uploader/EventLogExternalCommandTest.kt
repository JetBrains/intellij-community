// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.uploader

import com.intellij.internal.statistic.eventLog.*
import com.intellij.internal.statistic.eventLog.config.EventLogExternalRecorderConfig
import com.intellij.internal.statistic.eventLog.uploader.EventLogExternalUploader
import com.intellij.internal.statistic.uploader.EventLogUploaderCliParser
import com.intellij.internal.statistic.uploader.EventLogUploaderOptions.*
import com.intellij.internal.statistic.uploader.SendConfiguration
import com.intellij.openapi.application.PathManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import junit.framework.TestCase
import java.io.File

class EventLogExternalCommandTest : BasePlatformTestCase() {

  private fun createCommand(configByRecorder: Map<String, EventLogDeviceConfiguration>,
                            filesByRecorders: Map<String, List<String>>,
                            classPath: String = "/test/path1:/test/path2",
                            tempDir: File = File(PathManager.getTempPath(), "statistics-uploader")): Array<out String> {
    val appInfo = EventLogInternalApplicationInfo(false)
    return EventLogExternalUploader.createExternalUploadCommand(appInfo, configByRecorder, filesByRecorders, classPath, tempDir)
  }

  fun test_create_command_one_recorder() {
    val command = createCommand(
      mapOf("ABC" to TestDeviceConfiguration("test-device-id", 10, "test-machine-id", 15)),
      mapOf("ABC" to listOf("/path/to/log/file"))
    )

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
      mapOf(
        "ABC" to TestDeviceConfiguration("test-device-id", 10, "test-machine-id", 15),
        "DEF" to TestDeviceConfiguration("another-device-id", 65, "another-machine-id", 98)
      ),
      mapOf(
        "ABC" to listOf("/path/to/log/file"),
        "DEF" to listOf("/another/path/to/log")
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

  private fun doTestParse(configByRecorder: Map<String, EventLogDeviceConfiguration>,
                          filesByRecorders: Map<String, List<String>>,
                          vararg expectedConfigs: SendConfiguration) {
    val command = createCommand(configByRecorder, filesByRecorders)
    val configs = SendConfiguration.parseSendConfigurations(EventLogUploaderCliParser.parseOptions(command))

    TestCase.assertEquals(expectedConfigs.size, configs.size)
    for (expected in expectedConfigs) {
      val actual = configs.find { it.recorderId == expected.recorderId }
      TestCase.assertEquals(expected.deviceConfig!!.deviceId, actual!!.deviceConfig!!.deviceId)
      TestCase.assertEquals(expected.deviceConfig!!.bucket, actual.deviceConfig!!.bucket)
      TestCase.assertEquals(expected.deviceConfig!!.machineId, actual.deviceConfig!!.machineId)

      TestCase.assertEquals(
        expected.recorderConfig!!.getFilesToSendProvider().getFilesToSend(),
        actual.recorderConfig!!.getFilesToSendProvider().getFilesToSend()
      )
    }
  }

  fun test_send_command_parse_one_recorder() {
    doTestParse(
      mapOf("ABC" to TestDeviceConfiguration("test-device-id", 10, "test-machine-id", 15)),
      mapOf("ABC" to listOf("/path/to/log/file")),
      SendConfiguration(
        "ABC",
        DeviceConfiguration("test-device-id", 10, MachineId("test-machine-id", 15)),
        EventLogExternalRecorderConfig("ABC", listOf("/path/to/log/file"))
      )
    )
  }

  fun test_send_command_parse_multiple_log_files() {
    doTestParse(
      mapOf("ABC" to TestDeviceConfiguration("test-device-id", 10, "test-machine-id", 15)),
      mapOf("ABC" to listOf("/path/to/log/file", "/second/path/to/log/file", "/third/path/to/log/file")),
      SendConfiguration(
        "ABC",
        DeviceConfiguration("test-device-id", 10, MachineId("test-machine-id", 15)),
        EventLogExternalRecorderConfig("ABC", listOf("/path/to/log/file", "/second/path/to/log/file", "/third/path/to/log/file"))
      )
    )
  }

  fun test_send_command_parse_without_log_files() {
    doTestParse(
      mapOf("ABC" to TestDeviceConfiguration("test-device-id", 10, "test-machine-id", 15)),
      mapOf("ABC" to listOf()),
      SendConfiguration(
        "ABC",
        DeviceConfiguration("test-device-id", 10, MachineId("test-machine-id", 15)),
        EventLogExternalRecorderConfig("ABC", listOf())
      )
    )
  }

  fun test_send_command_parse_multiple_recorders() {
    doTestParse(
      mapOf(
        "ABC" to TestDeviceConfiguration("test-device-id", 10, "test-machine-id", 15),
        "DEF" to TestDeviceConfiguration("another-device-id", 65, "another-machine-id", 98)
      ),
      mapOf(
        "ABC" to listOf("/path/to/log/file"),
        "DEF" to listOf("/another/path/to/log")
      ),
      SendConfiguration(
        "ABC",
        DeviceConfiguration("test-device-id", 10, MachineId("test-machine-id", 15)),
        EventLogExternalRecorderConfig("ABC", listOf("/path/to/log/file"))
      ),
      SendConfiguration(
        "DEF",
        DeviceConfiguration("another-device-id", 65, MachineId("another-machine-id", 98)),
        EventLogExternalRecorderConfig("DEF", listOf("/another/path/to/log"))
      )
    )
  }
}

private class TestDeviceConfiguration(
  override val deviceId: String,
  override val bucket: Int,
  machineId: String,
  machineIdRevision: Int)
  : EventLogDeviceConfiguration {
  override val machineId = MachineId(machineId, machineIdRevision)
}