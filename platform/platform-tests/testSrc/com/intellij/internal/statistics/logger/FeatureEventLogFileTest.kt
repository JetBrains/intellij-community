// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.logger

import com.intellij.internal.statistic.config.eventLog.EventLogBuildType.*
import com.intellij.internal.statistic.eventLog.EventLogFile
import org.junit.Test
import java.io.File
import java.nio.file.Paths
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureEventLogFileTest {

  @Test
  fun `test choosing new file name`() {
    assertTrue(EventLogFile.create(Paths.get("tmp"), EAP, "").file.name.endsWith("-eap.log"))
    assertTrue(EventLogFile.create(Paths.get("tmp"), RELEASE, "").file.name.endsWith("-release.log"))

    assertFalse(EventLogFile.create(Paths.get("tmp"), EAP, "").file.name.endsWith("--eap.log"))
    assertFalse(EventLogFile.create(Paths.get("tmp"), RELEASE, "").file.name.endsWith("--release.log"))

    assertTrue(EventLogFile.create(Paths.get("tmp"), EAP, "193.0").file.name.endsWith("-193.0-eap.log"))
    assertTrue(EventLogFile.create(Paths.get("tmp"), RELEASE, "193.0").file.name.endsWith("-193.0-release.log"))

    assertTrue(EventLogFile.create(Paths.get("tmp"), EAP, "193.142.121").file.name.endsWith("-193.142.121-eap.log"))
    assertTrue(EventLogFile.create(Paths.get("tmp"), RELEASE, "193.142.121").file.name.endsWith("-193.142.121-release.log"))
  }

  @Test
  fun `test recording build type in file name`() {
    assertEquals(EAP, EventLogFile.create(Paths.get("tmp"), EAP, "").getType())
    assertEquals(RELEASE, EventLogFile.create(Paths.get("tmp"), RELEASE, "").getType())

    assertEquals(EAP, EventLogFile.create(Paths.get("tmp"), EAP, "193.0").getType())
    assertEquals(RELEASE, EventLogFile.create(Paths.get("tmp"), RELEASE, "193.0").getType())

    assertEquals(EAP, EventLogFile.create(Paths.get("tmp"), EAP, "193.124.12").getType())
    assertEquals(RELEASE, EventLogFile.create(Paths.get("tmp"), RELEASE, "193.124.12").getType())
  }

  @Test
  fun `test reading build type from file name`() {
    assertEquals(EAP, EventLogFile(File("a96c3f145eb7-193.2342.21-eap.log")).getType())
    assertEquals(RELEASE, EventLogFile(File("a96c3f145eb7-193.2342.21-release.log")).getType())

    assertEquals(EAP, EventLogFile(File("a96c3f145eb7-193.0-eap.log")).getType())
    assertEquals(RELEASE, EventLogFile(File("a96c3f145eb7-193.0-release.log")).getType())

    assertEquals(EAP, EventLogFile(File("-eap.log")).getType())
    assertEquals(RELEASE, EventLogFile(File("-release.log")).getType())

    assertEquals(EAP, EventLogFile(File("eap.log")).getType())
    assertEquals(RELEASE, EventLogFile(File("release.log")).getType())

    assertEquals(EAP, EventLogFile(File("a96c3f145eb7-eap.log")).getType())
    assertEquals(RELEASE, EventLogFile(File("a96c3f145eb7-release.log")).getType())

    assertEquals(EAP, EventLogFile(File("a96c3f145eb7-eap.txt")).getType())
    assertEquals(RELEASE, EventLogFile(File("a96c3f145eb7-release.txt")).getType())

    assertEquals(EAP, EventLogFile(File("a96c3f145eb7-eap")).getType())
    assertEquals(RELEASE, EventLogFile(File("a96c3f145eb7-release")).getType())

    assertEquals(EAP, EventLogFile(File("a96c3f145eb7-eap.log.txt")).getType())
    assertEquals(RELEASE, EventLogFile(File("a96c3f145eb7-release.log.txt")).getType())

    assertEquals(EAP, EventLogFile(File("a96c3f145eb7-eap.log.log.txt")).getType())
    assertEquals(RELEASE, EventLogFile(File("a96c3f145eb7-release.log.log.txt")).getType())

    assertEquals(EAP, EventLogFile(File("ff5aa023.1a56-43c1-87a7.94627105f091-eap.log")).getType())
    assertEquals(RELEASE, EventLogFile(File("ff5aa023.1a56-43c1-87a7.94627105f091-release.log")).getType())
  }

  @Test
  fun `test getting build type from invalid file name`() {
    assertEquals(UNKNOWN, EventLogFile(File("a96c3f145eb7-193.2342.21.log")).getType())
    assertEquals(UNKNOWN, EventLogFile(File("a96c3f145eb7-193.2342.21eap.log")).getType())
    assertEquals(UNKNOWN, EventLogFile(File("a96c3f145eb7eap.log")).getType())
    assertEquals(UNKNOWN, EventLogFile(File("eap-a96c3f145eb7-193.2342.21.log")).getType())
    assertEquals(UNKNOWN, EventLogFile(File("a96c3f145eb7.log")).getType())
    assertEquals(UNKNOWN, EventLogFile(File("ff5aa023-1a56-43c1-87a7-94627105f091.log")).getType())
    assertEquals(UNKNOWN, EventLogFile(File("94627105f091-eap-.log")).getType())
    assertEquals(UNKNOWN, EventLogFile(File("-.log")).getType())
    assertEquals(UNKNOWN, EventLogFile(File(".log")).getType())
    assertEquals(UNKNOWN, EventLogFile(File("a96c3f145eb7-.log")).getType())
    assertEquals(UNKNOWN, EventLogFile(File("a96c3f145eb7-")).getType())
    assertEquals(UNKNOWN, EventLogFile(File("eap-")).getType())
    assertEquals(UNKNOWN, EventLogFile(File("a96c3f145eb7-e")).getType())
  }
}